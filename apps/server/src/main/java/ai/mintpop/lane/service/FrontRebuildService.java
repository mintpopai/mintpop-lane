package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.response.FrontRebuildPreview;
import ai.mintpop.lane.response.FrontRebuildStatus;

/** 全体用户重算线路：后台任务，进程内互斥与状态 */
public interface FrontRebuildService {

    FrontRebuildStatus status();

    /** 只读预检，按页面表单里的设置值算 */
    FrontRebuildPreview preview(FrontSettings settings);

    /** 启动后台任务并立即返回；已有任务在跑报 FRONT_REBUILD_RUNNING，不排队 */
    void start();
}
