package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 提换机申请的入参：设备三要素 + 可空的一句理由。 */
@Data
public class DeviceRebindCreateRequest {

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称过长")
    private String name;

    @NotBlank(message = "操作系统不能为空")
    @Size(max = 64, message = "操作系统信息过长")
    private String os;

    @Size(max = 64, message = "机型信息过长")
    private String model;

    /** 理由，可空——不强制填写，强制只会逼出没有信息量的字符 */
    @Size(max = 255, message = "理由过长")
    private String reason;
}
