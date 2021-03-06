package cn.wizzer.app.web.modules.controllers.platform.sys;

import cn.wizzer.app.sys.modules.services.SysMsgService;
import cn.wizzer.app.sys.modules.services.SysMsgUserService;
import cn.wizzer.app.web.commons.slog.annotation.SLog;
import cn.wizzer.app.web.commons.utils.StringUtil;
import cn.wizzer.framework.base.Result;
import com.alibaba.dubbo.config.annotation.Reference;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.nutz.dao.Chain;
import org.nutz.dao.Cnd;
import org.nutz.dao.Sqls;
import org.nutz.dao.sql.Sql;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.lang.Strings;
import org.nutz.lang.Times;
import org.nutz.lang.util.NutMap;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.Ok;
import org.nutz.mvc.annotation.Param;

import javax.servlet.http.HttpServletRequest;

@IocBean
@At("/platform/sys/msg/user")
public class SysMsgUserController {
    private static final Log log = Logs.get();
    @Inject
    @Reference
    private SysMsgUserService sysMsgUserService;
    @Inject
    @Reference
    private SysMsgService sysMsgService;

    @At({"/all", "/all/?"})
    @Ok("beetl:/platform/sys/msg/user/indexAll.html")
    @RequiresPermissions("sys.msg.all")
    public void index(String type, HttpServletRequest req) {
        req.setAttribute("type", Strings.isBlank(type) ? "all" : type);
    }

    @At({"/read", "/read/?"})
    @Ok("beetl:/platform/sys/msg/user/indexRead.html")
    @RequiresPermissions("sys.msg.read")
    public void read(String type, HttpServletRequest req) {
        req.setAttribute("type", Strings.isBlank(type) ? "all" : type);

    }

    @At({"/unread", "/unread/?"})
    @Ok("beetl:/platform/sys/msg/user/indexUnread.html")
    @RequiresPermissions("sys.msg.unread")
    public void unread(String type, HttpServletRequest req) {
        req.setAttribute("type", Strings.isBlank(type) ? "all" : type);
    }

    @At("/data/?")
    @Ok("json:full")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    public Object data(String status, @Param("searchType") String type, @Param("pageNumber") int pageNumber, @Param("pageSize") int pageSize, @Param("pageOrderName") String pageOrderName, @Param("pageOrderBy") String pageOrderBy) {
        try {
            Cnd cnd = Cnd.NEW();
            if (Strings.isNotBlank(status) && "read".equals(status)) {
                cnd.and("a.status", "=", 1);
            }
            if (Strings.isNotBlank(status) && "unread".equals(status)) {
                cnd.and("a.status", "=", 0);
            }
            cnd.and("a.loginname", "=", StringUtil.getPlatformLoginname());
            cnd.and("a.delFlag", "=", false);
            cnd.desc("a.opAt");
            if (Strings.isNotBlank(type) && !"all".equals(type)) {
                cnd.and("b.type", "=", type);
            }
            Sql sql = Sqls.create("SELECT b.type,b.title,b.sendat,a.* FROM sys_msg b LEFT JOIN sys_msg_user a ON b.id=a.msgid $condition");
            sql.setCondition(cnd);
            Sql sqlCount = Sqls.create("SELECT count(*) FROM sys_msg b LEFT JOIN sys_msg_user a ON b.id=a.msgid $condition");
            sqlCount.setCondition(cnd);
            return Result.success().addData(sysMsgService.listPage(pageNumber, pageSize, sql, sqlCount));
        } catch (Exception e) {
            return Result.error();
        }
    }

    @At({"/delete/?", "/delete"})
    @Ok("json")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    @SLog(tag = "站内消息", msg = "${req.getAttribute('id')}")
    public Object delete(String id, @Param("ids") String[] ids, HttpServletRequest req) {
        try {
            if (ids != null && ids.length > 0) {
                sysMsgUserService.update(Chain.make("delFlag", true)
                        .add("opAt", Times.getTS()).add("opBy", StringUtil.getPlatformUid()), Cnd.where("id", "in", ids).and("loginname", "=", StringUtil.getPlatformLoginname()));
                req.setAttribute("id", org.apache.shiro.util.StringUtils.toString(ids));
            } else {
                sysMsgUserService.update(Chain.make("delFlag", true)
                        .add("opAt", Times.getTS()).add("opBy", StringUtil.getPlatformUid()), Cnd.where("id", "=", id).and("loginname", "=", StringUtil.getPlatformLoginname()));
                req.setAttribute("id", id);
            }
            sysMsgUserService.deleteCache(StringUtil.getPlatformLoginname());
            return Result.success();
        } catch (Exception e) {
            return Result.error();
        }
    }

    @At
    @Ok("json")
    @RequiresPermissions("sys.manager.msg")
    public Object unread_num() {
        try {
            NutMap nutMap = NutMap.NEW();
            nutMap.put("system", sysMsgUserService.count(Sqls.create("SELECT count(*) from sys_msg a,sys_msg_user b WHERE a.id=b.msgId AND a.type='system' AND a.delFlag=false AND b.status=0 AND b.delFlag=false AND b.loginname=@loginname").setParam("loginname", StringUtil.getPlatformLoginname())));
            nutMap.put("user", sysMsgUserService.count(Sqls.create("SELECT count(*) from sys_msg a,sys_msg_user b WHERE a.id=b.msgId AND a.type='user' AND a.delFlag=false AND b.status=0 AND b.delFlag=false AND b.loginname=@loginname").setParam("loginname", StringUtil.getPlatformLoginname())));
            return Result.success().addData(nutMap);
        } catch (Exception e) {
            return Result.error();
        }
    }

    @At("/status/read")
    @Ok("json")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    @SLog(tag = "站内消息", msg = "${req.getAttribute('id')}")
    public Object read(@Param("ids") String[] ids, HttpServletRequest req) {
        try {
            sysMsgUserService.update(Chain.make("status", 1).add("readAt", Times.getTS())
                    .add("opAt", Times.getTS()).add("opBy", StringUtil.getPlatformUid()), Cnd.where("id", "in", ids).and("loginname", "=", StringUtil.getPlatformLoginname()));
            sysMsgUserService.deleteCache(StringUtil.getPlatformLoginname());
            req.setAttribute("id", org.apache.shiro.util.StringUtils.toString(ids));
            return Result.success("system.success");
        } catch (Exception e) {
            return Result.error("system.error");
        }
    }

    @At("/status/readAll")
    @Ok("json")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    @SLog(tag = "站内消息", msg = "readAll")
    public Object readAll(HttpServletRequest req) {
        try {
            sysMsgUserService.update(Chain.make("status", 1).add("readAt", Times.getTS())
                    .add("opAt", Times.getTS()).add("opBy", StringUtil.getPlatformUid()), Cnd.where("loginname", "=", StringUtil.getPlatformLoginname()));
            sysMsgUserService.deleteCache(StringUtil.getPlatformLoginname());
            return Result.success();
        } catch (Exception e) {
            return Result.error();
        }
    }

    @At("/all/detail/?")
    @Ok("beetl:/platform/sys/msg/user/detailAll.html")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    public void allDetail(String id, HttpServletRequest req) {
        if (!Strings.isBlank(id)) {
            //判断用户是否是正常获取消息
            int num = sysMsgUserService.count(Cnd.where("msgid", "=", id).and("loginname", "=", StringUtil.getPlatformLoginname()));
            if (num > 0) {
                req.setAttribute("obj", sysMsgService.fetch(id));
            } else {
                req.setAttribute("obj", null);
            }
        } else {
            req.setAttribute("obj", null);
        }
    }

    @At("/read/detail/?")
    @Ok("beetl:/platform/sys/msg/user/detailRead.html")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    public void readDetail(String id, HttpServletRequest req) {
        if (!Strings.isBlank(id)) {
            //判断用户是否是正常获取消息
            int num = sysMsgUserService.count(Cnd.where("msgid", "=", id).and("loginname", "=", StringUtil.getPlatformLoginname()));
            if (num > 0) {
                req.setAttribute("obj", sysMsgService.fetch(id));
            } else {
                req.setAttribute("obj", null);
            }
        } else {
            req.setAttribute("obj", null);
        }
    }

    @At("/unread/detail/?")
    @Ok("beetl:/platform/sys/msg/user/detailUnread.html")
    @RequiresPermissions(value = {"sys.msg.all", "sys.msg.read", "sys.msg.unread"}, logical = Logical.OR)
    public void unreadDetail(String id, HttpServletRequest req) {
        if (!Strings.isBlank(id)) {
            //判断用户是否是正常获取消息
            int num = sysMsgUserService.count(Cnd.where("msgid", "=", id).and("loginname", "=", StringUtil.getPlatformLoginname()));
            if (num > 0) {
                req.setAttribute("obj", sysMsgService.fetch(id));
            } else {
                req.setAttribute("obj", null);
            }
        } else {
            req.setAttribute("obj", null);
        }
    }

}
