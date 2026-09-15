package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 绑定设备的入参：设备三要素。机器码不在这里——它在请求头里，
 * 免得同一份信息有两个来源、还可能对不上。
 */
@Data
public class DeviceBindRequest {

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称过长")
    private String name;

    @NotBlank(message = "操作系统不能为空")
    @Size(max = 64, message = "操作系统信息过长")
    private String os;

    /** 机型；客户端取不到时传空串，不校验非空 */
    @Size(max = 64, message = "机型信息过长")
    private String model;
}
