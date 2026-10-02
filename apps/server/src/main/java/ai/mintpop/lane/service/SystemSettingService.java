package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;

/** 全局配置的类型化读写。表里没有的键按 SettingKey 默认值走 */
public interface SystemSettingService {

    FrontSettings frontSettings();

    /** 校验范围后三项全部写库并返回新值。只改配置、不动任何人的线路：全体重算只能由管理员手动触发 */
    FrontSettings updateFrontSettings(FrontSettingsUpdateRequest request);
}
