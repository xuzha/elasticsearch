/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.repositories.s3;

import com.amazonaws.Protocol;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import com.amazonaws.util.Base64;
import org.elasticsearch.cloud.aws.AwsS3Service;
import org.elasticsearch.cloud.aws.AwsS3Service.CLOUD_S3;
import org.elasticsearch.cloud.aws.blobstore.S3BlobStore;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.BlobStore;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.snapshots.IndexShardRepository;
import org.elasticsearch.repositories.RepositoryException;
import org.elasticsearch.repositories.RepositoryName;
import org.elasticsearch.repositories.RepositorySettings;
import org.elasticsearch.repositories.blobstore.BlobStoreRepository;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.function.Function;

/**
 * Shared file system implementation of the BlobStoreRepository
 * <p>
 * Shared file system repository supports the following settings
 * <dl>
 * <dt>{@code bucket}</dt><dd>S3 bucket</dd>
 * <dt>{@code region}</dt><dd>S3 region. Defaults to us-east</dd>
 * <dt>{@code base_path}</dt><dd>Specifies the path within bucket to repository data. Defaults to root directory.</dd>
 * <dt>{@code concurrent_streams}</dt><dd>Number of concurrent read/write stream (per repository on each node). Defaults to 5.</dd>
 * <dt>{@code chunk_size}</dt><dd>Large file can be divided into chunks. This parameter specifies the chunk size. Defaults to not chucked.</dd>
 * <dt>{@code compress}</dt><dd>If set to true metadata files will be stored compressed. Defaults to false.</dd>
 * </dl>
 */
public class S3Repository extends BlobStoreRepository {

    public final static String TYPE = "s3";

    /**
     * Global S3 repositories settings. Starting with: repositories.s3
     */
    public interface Repositories {
        /**
         * repositories.s3.access_key: AWS Access key specific for all S3 Repositories API calls. Defaults to cloud.aws.s3.access_key.
         * @see CLOUD_S3#KEY_SETTING
         */
        Setting<String> KEY_SETTING =
            new Setting<>("repositories.s3.access_key", CLOUD_S3.KEY_SETTING, Function.identity(), Property.NodeScope);
        /**
         * repositories.s3.secret_key: AWS Secret key specific for all S3 Repositories API calls. Defaults to cloud.aws.s3.secret_key.
         * @see CLOUD_S3#SECRET_SETTING
         */
        Setting<String> SECRET_SETTING =
            new Setting<>("repositories.s3.secret_key", CLOUD_S3.SECRET_SETTING, Function.identity(), Property.NodeScope);
        /**
         * repositories.s3.region: Region specific for all S3 Repositories API calls. Defaults to cloud.aws.s3.region.
         * @see CLOUD_S3#REGION_SETTING
         */
        Setting<String> REGION_SETTING =
            new Setting<>("repositories.s3.region", CLOUD_S3.REGION_SETTING, s -> s.toLowerCase(Locale.ROOT), Property.NodeScope);
        /**
         * repositories.s3.endpoint: Endpoint specific for all S3 Repositories API calls. Defaults to cloud.aws.s3.endpoint.
         * @see CLOUD_S3#ENDPOINT_SETTING
         */
        Setting<String> ENDPOINT_SETTING =
            new Setting<>("repositories.s3.endpoint", CLOUD_S3.ENDPOINT_SETTING, s -> s.toLowerCase(Locale.ROOT), Property.NodeScope);
        /**
         * repositories.s3.protocol: Protocol specific for all S3 Repositories API calls. Defaults to cloud.aws.s3.protocol.
         * @see CLOUD_S3#PROTOCOL_SETTING
         */
        Setting<Protocol> PROTOCOL_SETTING =
            new Setting<>("repositories.s3.protocol", CLOUD_S3.PROTOCOL_SETTING, s -> Protocol.valueOf(s.toUpperCase(Locale.ROOT)), Property.NodeScope);
        /**
         * repositories.s3.bucket: The name of the bucket to be used for snapshots.
         */
        Setting<String> BUCKET_SETTING = Setting.simpleString("repositories.s3.bucket", Property.NodeScope);
        /**
         * repositories.s3.buffer_size: Minimum threshold below which the chunk is uploaded using a single request. Beyond this threshold,
         * the S3 repository will use the AWS Multipart Upload API to split the chunk into several parts, each of buffer_size length, and
         * to upload each part in its own request. Note that setting a buffer size lower than 5mb is not allowed since it will prevents the
         * use of the Multipart API and may result in upload errors. Defaults to 100m.
         */
        Setting<ByteSizeValue> BUFFER_SIZE_SETTING =
            Setting.byteSizeSetting("repositories.s3.buffer_size", new ByteSizeValue(100, ByteSizeUnit.MB),
                new ByteSizeValue(5, ByteSizeUnit.MB), new ByteSizeValue(5, ByteSizeUnit.TB), Property.NodeScope);
        /**
         * repositories.s3.max_retries: Number of retries in case of S3 errors. Defaults to 3.
         */
        Setting<Integer> MAX_RETRIES_SETTING = Setting.intSetting("repositories.s3.max_retries", 3, Property.NodeScope);
        /**
         * repositories.s3.chunk_size: Big files can be broken down into chunks during snapshotting if needed. Defaults to 1g.
         */
        Setting<ByteSizeValue> CHUNK_SIZE_SETTING =
            Setting.byteSizeSetting("repositories.s3.chunk_size", new ByteSizeValue(1, ByteSizeUnit.GB),
                new ByteSizeValue(5, ByteSizeUnit.MB), new ByteSizeValue(5, ByteSizeUnit.TB), Property.NodeScope);
        /**
         * repositories.s3.compress: When set to true metadata files are stored in compressed format. This setting doesn’t affect index
         * files that are already compressed by default. Defaults to false.
         */
        Setting<Boolean> COMPRESS_SETTING = Setting.boolSetting("repositories.s3.compress", false, Property.NodeScope);
        /**
         * repositories.s3.storage_class: Sets the S3 storage class type for the backup files. Values may be standard, reduced_redundancy,
         * standard_ia. Defaults to standard.
         */
        Setting<String> STORAGE_CLASS_SETTING = Setting.simpleString("repositories.s3.storage_class", Property.NodeScope);
        /**
         * repositories.s3.canned_acl: The S3 repository supports all S3 canned ACLs : private, public-read, public-read-write,
         * authenticated-read, log-delivery-write, bucket-owner-read, bucket-owner-full-control. Defaults to private.
         */
        Setting<String> CANNED_ACL_SETTING = Setting.simpleString("repositories.s3.canned_acl", Property.NodeScope);
        /**
         * repositories.s3.base_path: Specifies the path within bucket to repository data. Defaults to root directory.
         */
        Setting<String> BASE_PATH_SETTING = Setting.simpleString("repositories.s3.base_path", Property.NodeScope);
        /**
         * repositories.s3.encryption.server.enabled: When set to true files are encrypted on server side using AES256 algorithm.
         * Defaults to false.
         */
        Setting<Boolean> SERVER_SIDE_ENCRYPTION_SETTING =
                Setting.boolSetting("repositories.s3.encryption.server.enabled", false, Property.NodeScope);

        /**
         * repositories.s3.encryption.client.enabled: When set to true files are encrypted on client side.
         */
        Setting<Boolean> CLIENT_SIDE_ENCRYPTION_SETTING =
                Setting.boolSetting("repositories.s3.encryption.client.enabled", false, Property.NodeScope);

        /**
         * repositories.s3.encryption.client.symmetric_key: Specifies the Base64-encoded AES symmetric-key (128, 192 or 256 bits)
         */
        Setting<String> SYMMETRIC_KEY_SETTING = Setting.simpleString("repositories.s3.encryption.client.symmetric_key", Property.NodeScope);

        /**
         * repositories.s3.encryption.client.public_key: Specifies the Base64-encoded RSA public key
         */
        Setting<String> PUBLIC_KEY_SETTING = Setting.simpleString("repositories.s3.encryption.client.public_key", Property.NodeScope);

        /**
         * repositories.s3.encryption.client.private_key: Specifies the Base64-encoded RSA private key
         */
        Setting<String> PRIVATE_KEY_SETTING = Setting.simpleString("repositories.s3.encryption.client.private_key", Property.NodeScope);

    }

    /**
     * Per S3 repository specific settings. Same settings as Repositories settings but without the repositories.s3 prefix.
     * If undefined, they use the repositories.s3.xxx equivalent setting.
     */
    public interface Repository {
        /**
         * access_key
         * @see  Repositories#KEY_SETTING
         */
        Setting<String> KEY_SETTING = Setting.simpleString("access_key", Property.NodeScope, Property.Filtered);
        /**
         * secret_key
         * @see  Repositories#SECRET_SETTING
         */
        Setting<String> SECRET_SETTING = Setting.simpleString("secret_key", Property.NodeScope, Property.Filtered);
        /**
         * bucket
         * @see  Repositories#BUCKET_SETTING
         */
        Setting<String> BUCKET_SETTING = Setting.simpleString("bucket", Property.NodeScope);
        /**
         * endpoint
         * @see  Repositories#ENDPOINT_SETTING
         */
        Setting<String> ENDPOINT_SETTING = Setting.simpleString("endpoint", Property.NodeScope);
        /**
         * protocol
         * @see  Repositories#PROTOCOL_SETTING
         */
        Setting<Protocol> PROTOCOL_SETTING =
            new Setting<>("protocol", "https", s -> Protocol.valueOf(s.toUpperCase(Locale.ROOT)), Property.NodeScope);
        /**
         * region
         * @see  Repositories#REGION_SETTING
         */
        Setting<String> REGION_SETTING = new Setting<>("region", "", s -> s.toLowerCase(Locale.ROOT), Property.NodeScope);
        /**
         * buffer_size
         * @see  Repositories#BUFFER_SIZE_SETTING
         */
        Setting<ByteSizeValue> BUFFER_SIZE_SETTING =
            Setting.byteSizeSetting("buffer_size", new ByteSizeValue(100, ByteSizeUnit.MB),
                new ByteSizeValue(5, ByteSizeUnit.MB), new ByteSizeValue(5, ByteSizeUnit.TB), Property.NodeScope);
        /**
         * max_retries
         * @see  Repositories#MAX_RETRIES_SETTING
         */
        Setting<Integer> MAX_RETRIES_SETTING = Setting.intSetting("max_retries", 3, Property.NodeScope);
        /**
         * chunk_size
         * @see  Repositories#CHUNK_SIZE_SETTING
         */
        Setting<ByteSizeValue> CHUNK_SIZE_SETTING =
            Setting.byteSizeSetting("chunk_size", new ByteSizeValue(1, ByteSizeUnit.GB),
                new ByteSizeValue(5, ByteSizeUnit.MB), new ByteSizeValue(5, ByteSizeUnit.TB), Property.NodeScope);
        /**
         * compress
         * @see  Repositories#COMPRESS_SETTING
         */
        Setting<Boolean> COMPRESS_SETTING = Setting.boolSetting("compress", false, Property.NodeScope);
        /**
         * storage_class
         * @see  Repositories#STORAGE_CLASS_SETTING
         */
        Setting<String> STORAGE_CLASS_SETTING = Setting.simpleString("storage_class", Property.NodeScope);
        /**
         * canned_acl
         * @see  Repositories#CANNED_ACL_SETTING
         */
        Setting<String> CANNED_ACL_SETTING = Setting.simpleString("canned_acl", Property.NodeScope);
        /**
         * base_path
         * @see  Repositories#BASE_PATH_SETTING
         */
        Setting<String> BASE_PATH_SETTING = Setting.simpleString("base_path", Property.NodeScope);
        /**
         * server_side_encryption
         * @see  Repositories#SERVER_SIDE_ENCRYPTION_SETTING
         */
        Setting<Boolean> SERVER_SIDE_ENCRYPTION_SETTING =
                Setting.boolSetting("encryption.server.enabled", false, Property.NodeScope);

        /**
         * client_side_encryption
         * @see  Repositories#CLIENT_SIDE_ENCRYPTION_SETTING
         */
        Setting<Boolean> CLIENT_SIDE_ENCRYPTION_SETTING =
                Setting.boolSetting("encryption.client.enabled", false, Property.NodeScope);

        /**
         * base_path
         * @see  Repositories#SYMMETRIC_KEY_SETTING
         */
        Setting<String> SYMMETRIC_KEY_SETTING = Setting.simpleString("encryption.client.symmetric_key", Property.NodeScope);

        /**
         * base_path
         * @see  Repositories#PUBLIC_KEY_SETTING
         */
        Setting<String> PUBLIC_KEY_SETTING = Setting.simpleString("encryption.client.public_key", Property.NodeScope);

        /**
         * base_path
         * @see  Repositories#PRIVATE_KEY_SETTING
         */
        Setting<String> PRIVATE_KEY_SETTING = Setting.simpleString("encryption.client.private_key", Property.NodeScope);
    }

    private final S3BlobStore blobStore;

    private final BlobPath basePath;

    private ByteSizeValue chunkSize;

    private boolean compress;

    /**
     * Constructs new shared file system repository
     *
     * @param name                 repository name
     * @param repositorySettings   repository settings
     * @param indexShardRepository index shard repository
     * @param s3Service            S3 service
     */
    @Inject
    public S3Repository(RepositoryName name, RepositorySettings repositorySettings, IndexShardRepository indexShardRepository, AwsS3Service s3Service) throws IOException {
        super(name.getName(), repositorySettings, indexShardRepository);

        String bucket = getValue(repositorySettings, Repository.BUCKET_SETTING, Repositories.BUCKET_SETTING);
        if (bucket == null) {
            throw new RepositoryException(name.name(), "No bucket defined for s3 gateway");
        }

        String endpoint = getValue(repositorySettings, Repository.ENDPOINT_SETTING, Repositories.ENDPOINT_SETTING);
        Protocol protocol = getValue(repositorySettings, Repository.PROTOCOL_SETTING, Repositories.PROTOCOL_SETTING);
        String region = getValue(repositorySettings, Repository.REGION_SETTING, Repositories.REGION_SETTING);
        // If no region is defined either in region, repositories.s3.region, cloud.aws.s3.region or cloud.aws.region
        // we fallback to Default bucket - null
        if (Strings.isEmpty(region)) {
            region = null;
        }

        boolean serverSideEncryption = getValue(repositorySettings, Repository.SERVER_SIDE_ENCRYPTION_SETTING, Repositories.SERVER_SIDE_ENCRYPTION_SETTING);
        ByteSizeValue bufferSize = getValue(repositorySettings, Repository.BUFFER_SIZE_SETTING, Repositories.BUFFER_SIZE_SETTING);
        Integer maxRetries = getValue(repositorySettings, Repository.MAX_RETRIES_SETTING, Repositories.MAX_RETRIES_SETTING);
        this.chunkSize = getValue(repositorySettings, Repository.CHUNK_SIZE_SETTING, Repositories.CHUNK_SIZE_SETTING);
        this.compress = getValue(repositorySettings, Repository.COMPRESS_SETTING, Repositories.COMPRESS_SETTING);

        // We make sure that chunkSize is bigger or equal than/to bufferSize
        if (this.chunkSize.getBytes() < bufferSize.getBytes()) {
            throw new RepositoryException(name.name(), Repository.CHUNK_SIZE_SETTING.getKey() + " (" + this.chunkSize +
                ") can't be lower than " + Repository.BUFFER_SIZE_SETTING.getKey() + " (" + bufferSize + ").");
        }

        // Parse and validate the user's S3 Storage Class setting
        String storageClass = getValue(repositorySettings, Repository.STORAGE_CLASS_SETTING, Repositories.STORAGE_CLASS_SETTING);
        String cannedACL = getValue(repositorySettings, Repository.CANNED_ACL_SETTING, Repositories.CANNED_ACL_SETTING);

        logger.debug("using bucket [{}], region [{}], endpoint [{}], protocol [{}], chunk_size [{}], server_side_encryption [{}], buffer_size [{}], max_retries [{}], cannedACL [{}], storageClass [{}]",
                bucket, region, endpoint, protocol, chunkSize, serverSideEncryption, bufferSize, maxRetries, cannedACL, storageClass);

        String key = getValue(repositorySettings, Repository.KEY_SETTING, Repositories.KEY_SETTING);
        String secret = getValue(repositorySettings, Repository.SECRET_SETTING, Repositories.SECRET_SETTING);

        // parse and validate the client side encryption setting
        boolean clientSideEncryption = getValue(repositorySettings, Repository.CLIENT_SIDE_ENCRYPTION_SETTING, Repositories.CLIENT_SIDE_ENCRYPTION_SETTING);

        String symmetricKeyBase64 = getValue(repositorySettings, Repository.SYMMETRIC_KEY_SETTING, Repositories.SYMMETRIC_KEY_SETTING);
        String publicKeyBase64 = getValue(repositorySettings, Repository.PUBLIC_KEY_SETTING, Repositories.PUBLIC_KEY_SETTING);
        String privateKeyBase64 = getValue(repositorySettings, Repository.PRIVATE_KEY_SETTING, Repositories.PRIVATE_KEY_SETTING);

        EncryptionMaterials clientSideEncryptionMaterials = null;

        if (clientSideEncryption)
            clientSideEncryptionMaterials = initClientSideEncryption(symmetricKeyBase64, publicKeyBase64, privateKeyBase64, name);

        blobStore = new S3BlobStore(
                settings,
                s3Service.client(endpoint, protocol, region, key, secret, maxRetries, clientSideEncryptionMaterials),
                bucket,
                region,
                serverSideEncryption,
                bufferSize,
                maxRetries,
                cannedACL,
                storageClass
        );

        String basePath = getValue(repositorySettings, Repository.BASE_PATH_SETTING, Repositories.BASE_PATH_SETTING);
        if (Strings.hasLength(basePath)) {
            BlobPath path = new BlobPath();
            for(String elem : Strings.splitStringToArray(basePath, '/')) {
                path = path.add(elem);
            }
            this.basePath = path;
        } else {
            this.basePath = BlobPath.cleanPath();
        }

    }


    /**
     * Init and verify initClientSideEncryption settings
     */
    private EncryptionMaterials initClientSideEncryption(String symmetricKey, String publicKey, String privateKey, RepositoryName name) {

        EncryptionMaterials clientSideEncryptionMaterials = null;

        if (Strings.isNullOrEmpty(symmetricKey) == false && (Strings.isNullOrEmpty(publicKey) == false || Strings.isNullOrEmpty(privateKey) == false)) {
            throw new RepositoryException(name.name(), "Client-side encryption: You can't specify a symmetric key AND a public/private key pair");
        }

        if (Strings.isNullOrEmpty(symmetricKey) == false || Strings.isNullOrEmpty(publicKey) == false || Strings.isNullOrEmpty(privateKey) == false) {
            try {
                // Check crypto
                if (Cipher.getMaxAllowedKeyLength("AES") < 256) {
                    throw new RepositoryException(name.name(), "Client-side encryption: Please install the Java Cryptography Extension");
                }

                // Transform the keys in a EncryptionMaterials
                if (Strings.isNullOrEmpty(symmetricKey) == false) {
                    clientSideEncryptionMaterials = new EncryptionMaterials(new SecretKeySpec(Base64.decode(symmetricKey), "AES"));
                } else {
                    if (Strings.isNullOrEmpty(publicKey) || Strings.isNullOrEmpty(privateKey)) {
                        String missingKey = Strings.isNullOrEmpty(publicKey) ? "public key" : "private key";
                        throw new RepositoryException(name.name(), "Client-side encryption: " + missingKey + " is missing");
                    }

                    clientSideEncryptionMaterials = new EncryptionMaterials(new KeyPair(
                            KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(publicKey))),
                            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privateKey)))));
                }

            } catch (IllegalArgumentException e) {
                throw new RepositoryException(name.name(), "Client-side encryption: Error decoding your keys: " + e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                throw new RepositoryException(name.name(), e.getMessage());
            } catch (InvalidKeySpecException e) {
                throw new RepositoryException(name.name(), e.getMessage());
            }
        }

        return clientSideEncryptionMaterials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BlobStore blobStore() {
        return blobStore;
    }

    @Override
    protected BlobPath basePath() {
        return basePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isCompress() {
        return compress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ByteSizeValue chunkSize() {
        return chunkSize;
    }

    public static <T> T getValue(RepositorySettings repositorySettings,
                                 Setting<T> repositorySetting,
                                 Setting<T> repositoriesSetting) {
        if (repositorySetting.exists(repositorySettings.settings())) {
            return repositorySetting.get(repositorySettings.settings());
        } else {
            return repositoriesSetting.get(repositorySettings.globalSettings());
        }
    }
}
