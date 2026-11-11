package io.github.wrqtec.content.safe.provider.tencent.internal;

import java.util.Map;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ims.v20201229.ImsClient;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskRequest;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskResponse;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.vm.v20210922.VmClient;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskRequest;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskResponse;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailRequest;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailResponse;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.provider.tencent.TencentProviderProperties;
import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;

/**
 * 基于腾讯云 SDK 的 {@link TencentClientInvoker} 实现。唯一触碰
 * {@link TmsClient}/{@link ImsClient}/{@link VmClient} 的类；构造期只装配凭证与
 * endpoint，不做任何远程调用（铁律 §5.7 启动轻量）
 *
 * @author L.David
 */
public final class TencentSdkClientInvoker implements TencentClientInvoker {

    /** extra 配置键：连接超时（毫秒） */
    public static final String EXTRA_CONNECT_TIMEOUT_MS = "connect-timeout-ms";

    /** extra 配置键：读取超时（秒，SDK HttpProfile 单位为秒） */
    public static final String EXTRA_READ_TIMEOUT_SECONDS = "read-timeout-seconds";

    /** 默认连接超时（毫秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /** 默认读取超时（秒） */
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 10;

    private final TmsClient tmsClient;
    private final ImsClient imsClient;
    private final VmClient vmClient;

    public TencentSdkClientInvoker(Credentials credentials, TencentProviderProperties properties) {
        Credential credential = credentials.securityToken() == null || credentials.securityToken().isBlank()
                ? new Credential(credentials.accessKeyId(), credentials.accessKeySecret())
                : new Credential(credentials.accessKeyId(), credentials.accessKeySecret(),
                        credentials.securityToken());
        // 三个产品域名字面量不同，profile 各建一份（HttpProfile endpoint 为单值）
        this.tmsClient = new TmsClient(credential, properties.getRegion(),
                newClientProfile(properties, tmsEndpoint(properties.getRegion())));
        this.imsClient = new ImsClient(credential, properties.getRegion(),
                newClientProfile(properties, imsEndpoint(properties.getRegion())));
        this.vmClient = new VmClient(credential, properties.getRegion(),
                newClientProfile(properties, vmEndpoint(properties.getRegion())));
    }

    private static ClientProfile newClientProfile(TencentProviderProperties properties, String endpoint) {
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(endpoint);
        Map<String, String> extra = properties.getExtra();
        Integer readTimeout = intExtra(extra, EXTRA_READ_TIMEOUT_SECONDS, null);
        if (readTimeout != null) {
            httpProfile.setReadTimeout(readTimeout);
        }
        ClientProfile profile = new ClientProfile();
        profile.setHttpProfile(httpProfile);
        return profile;
    }

    /** TMS/IMS/VM 各自独立域名（动作由客户端类区分，endpoint 域名互不可混用） */
    static String tmsEndpoint(String region) {
        return "tms." + region + ".tencentcloudapi.com";
    }

    static String imsEndpoint(String region) {
        return "ims." + region + ".tencentcloudapi.com";
    }

    static String vmEndpoint(String region) {
        return "vm." + region + ".tencentcloudapi.com";
    }

    private static Integer intExtra(Map<String, String> extra, String key, Integer defaultValue) {
        String value = extra.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException("tencent extra '" + key + "' must be an integer");
        }
    }

    @Override
    public TextModerationResponse textModeration(TextModerationRequest request) throws Exception {
        return tmsClient.TextModeration(request);
    }

    @Override
    public ImageModerationResponse imageModeration(ImageModerationRequest request) throws Exception {
        return imsClient.ImageModeration(request);
    }

    @Override
    public CreateImageModerationAsyncTaskResponse createImageModerationAsyncTask(
            CreateImageModerationAsyncTaskRequest request) throws Exception {
        return imsClient.CreateImageModerationAsyncTask(request);
    }

    @Override
    public CreateVideoModerationTaskResponse createVideoModerationTask(
            CreateVideoModerationTaskRequest request) throws Exception {
        return vmClient.CreateVideoModerationTask(request);
    }

    @Override
    public DescribeTaskDetailResponse describeTaskDetail(DescribeTaskDetailRequest request) throws Exception {
        return vmClient.DescribeTaskDetail(request);
    }
}
