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

package org.elasticsearch.plugin.repository.s3;

import org.elasticsearch.SpecialPermission;
import org.elasticsearch.cloud.aws.AwsS3Service;
import org.elasticsearch.cloud.aws.S3Module;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.index.snapshots.blobstore.BlobStoreIndexShardRepository;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.repositories.RepositoriesModule;
import org.elasticsearch.repositories.s3.S3Repository;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 *
 */
public class S3RepositoryPlugin extends Plugin {

    // ClientConfiguration clinit has some classloader problems
    // TODO: fix that
    static {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                try {
                    Class.forName("com.amazonaws.ClientConfiguration");
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        });
    }

    @Override
    public String name() {
        return "repository-s3";
    }

    @Override
    public String description() {
        return "S3 Repository Plugin";
    }

    @Override
    public Collection<Module> nodeModules() {
        Collection<Module> modules = new ArrayList<>();
        modules.add(new S3Module());
        return modules;
    }

    @Override
    @SuppressWarnings("rawtypes") // Supertype declaration has raw types
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        return Collections.<Class<? extends LifecycleComponent>>singleton(S3Module.getS3ServiceImpl());
    }

    public void onModule(RepositoriesModule repositoriesModule) {
        repositoriesModule.registerRepository(S3Repository.TYPE, S3Repository.class, BlobStoreIndexShardRepository.class);
    }

    public void onModule(SettingsModule settingsModule) {
        // Register global cloud aws settings: cloud.aws (might have been registered in ec2 plugin)
        registerSettingIfMissing(settingsModule, AwsS3Service.KEY_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.SECRET_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.PROTOCOL_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.PROXY_HOST_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.PROXY_PORT_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.PROXY_USERNAME_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.PROXY_PASSWORD_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.SIGNER_SETTING);
        registerSettingIfMissing(settingsModule, AwsS3Service.REGION_SETTING);

        // Register S3 specific settings: cloud.aws.s3
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.KEY_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.SECRET_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.PROTOCOL_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.PROXY_HOST_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.PROXY_PORT_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.PROXY_USERNAME_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.PROXY_PASSWORD_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.SIGNER_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.REGION_SETTING);
        settingsModule.registerSetting(AwsS3Service.CLOUD_S3.ENDPOINT_SETTING);

        // Register S3 repositories settings: repositories.s3
        settingsModule.registerSetting(S3Repository.Repositories.KEY_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.SECRET_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.BUCKET_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.REGION_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.ENDPOINT_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.PROTOCOL_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.SERVER_SIDE_ENCRYPTION_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.BUFFER_SIZE_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.MAX_RETRIES_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.CHUNK_SIZE_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.COMPRESS_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.STORAGE_CLASS_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.CANNED_ACL_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.BASE_PATH_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.PRIVATE_KEY_SETTING);
        settingsModule.registerSetting(S3Repository.Repositories.PUBLIC_KEY);
        settingsModule.registerSetting(S3Repository.Repositories.SYMMETRIC_KEY_SETTING);

        // Register S3 single repository settings
        settingsModule.registerSetting(S3Repository.Repository.KEY_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.SECRET_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.BUCKET_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.ENDPOINT_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.PROTOCOL_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.REGION_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.SERVER_SIDE_ENCRYPTION_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.BUFFER_SIZE_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.MAX_RETRIES_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.CHUNK_SIZE_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.COMPRESS_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.STORAGE_CLASS_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.CANNED_ACL_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.BASE_PATH_SETTING);
        settingsModule.registerSetting(S3Repository.Repository.CLIENT_PRIVATE_KEY);
        settingsModule.registerSetting(S3Repository.Repository.CLIENT_PUBLIC_KEY);
        settingsModule.registerSetting(S3Repository.Repository.CLIENT_SYMMETRIC_KEY);
    }

    /**
     * We manage potential duplicates between s3 and ec2 plugins (cloud.aws.xxx)
     */
    private void registerSettingIfMissing(SettingsModule settingsModule, Setting<?> setting) {
        if (settingsModule.exists(setting) == false) {
            settingsModule.registerSetting(setting);
        }
    }
}
