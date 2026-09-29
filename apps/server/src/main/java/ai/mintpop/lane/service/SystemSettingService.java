package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;

/** 全局配置的类型化读写。表里没有的键按 SettingKey 默认值走 */
public interface SystemSettingService {

    FrontSettings frontSettings();

    /** 校验范围后三项全部写库；返回旧新两份，调用方据 changed() 决定是否触发全体重算 */
    FrontSettingsChange updateFrontSettings(FrontSettingsUpdateRequest request);

    record FrontSettingsChange(FrontSettings previous, FrontSettings current) {
        public boolean changed() {
            return !previous.equals(current);
        }
    }
}
