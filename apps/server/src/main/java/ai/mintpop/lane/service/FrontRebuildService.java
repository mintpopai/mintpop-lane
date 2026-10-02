package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.response.FrontRebuildPreview;
import ai.mintpop.lane.response.FrontRebuildStatus;

/** 全体用户重算线路：后台任务，进程内互斥与状态 */
public interface FrontRebuildService {

    FrontRebuildStatus status();

    /** 只读预检，按页面表单里的设置值与重算方式算 */
    FrontRebuildPreview preview(FrontSettings settings, boolean keepManual);

    /**
     * 启动后台任务并立即返回；已有任务在跑报 FRONT_REBUILD_RUNNING，不排队。
     * keepManual 为 true 时管理员手动指定的列表原样保留，否则所有人从零重排
     */
    void start(boolean keepManual);
}
