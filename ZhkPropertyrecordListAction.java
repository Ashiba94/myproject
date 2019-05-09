package com.epoint.zhyw.zhkpropertyrecord.action;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Scope;
import com.epoint.zhyw.zhkpropertyrecord.api.entity.ZhkPropertyrecord;
import com.epoint.zhyw.zhkzjgcjcfa.api.entity.ZhkZjgcjcfa;
import com.aspose.words.Document;
import com.epoint.basic.authentication.UserSession;
import com.epoint.basic.controller.BaseController;
import com.epoint.basic.faces.export.ExportModel;
import com.epoint.core.dto.model.DataGridModel;
import com.epoint.basic.faces.util.DataUtil;
import com.epoint.core.dto.model.SelectItem;
import com.epoint.core.grammar.Record;
import com.epoint.core.utils.classpath.ClassPathUtil;
import com.epoint.core.utils.config.ConfigUtil;
import com.epoint.core.utils.date.EpointDateUtil;
import com.epoint.core.utils.string.StringUtil;
import com.epoint.frame.service.attach.api.IAttachService;
import com.epoint.frame.service.attach.entity.FrameAttachInfo;
import com.epoint.frame.service.metadata.mis.util.ListGenerator;
import com.epoint.szjs.approvalconfiguration.audittask.api.IAuditProjectService;
import com.epoint.szjs.approvalconfiguration.audittask.entity.AuditProject;
import com.epoint.szjs.common.CommonZLAQ;
import com.epoint.szjs.safe.common.api.ISafeProjectService;

import org.springframework.beans.factory.annotation.Autowired;

import com.epoint.zhyw.zhkpilefoundation.action.PrintFuction;
import com.epoint.zhyw.zhkpropertyrecord.api.IZhkPropertyrecordService;

/**
 * 起重产权备案表list页面对应的后台
 * 
 * @author Thaler
 * @version [版本号, 2019-01-29 10:04:03]
 */
@RestController("zhkpropertyrecordlistaction")
@Scope("request")
public class ZhkPropertyrecordListAction extends BaseController
{
    @Autowired
    private IZhkPropertyrecordService service;

    /**
     * 起重产权备案表实体对象
     */
    private ZhkPropertyrecord dataBean;

    /**
     * 表格控件model
     */
    private DataGridModel<Record> model;

    /**
     * 导出模型
     */
    private ExportModel exportModel;

    // 审核状态搜索条件
    private String status = "所有";

    @Autowired
    private ISafeProjectService safeprojectservice;

    // 业务表名
    private String Sql_Table_Name = "ZHK_PropertyRecord";

    @Autowired
    private IAttachService attachService;

    @Autowired
    private IAuditProjectService projectservice;

    public void pageLoad() {
    }

    /**
     * 删除选定
     * 
     */
    public void deleteSelect() {
        boolean flag = true;
        List<String> select = getDataGridData().getSelectKeys();
        for (String sel : select) {
            // 查找申请状态 0--未提交
            AuditProject auditProjectDataBean = projectservice.find(sel);
            if ("0".equals(auditProjectDataBean.getAuditstatus())
                    || "5".equals(auditProjectDataBean.getAuditstatus())) {
                service.deleteByGuid(sel);
                projectservice.deleteByGuid(sel);
            }
            else {
                flag = false;
            }
        }
        if (flag == true) {
            addCallbackParam("msg", "删除成功");
        }
        else {
            addCallbackParam("msg", "存在无法删除的数据，请重新选择！");
        }
    }

    public DataGridModel<Record> getDataGridData() {
        // 获得表格对象
        if (model == null) {
            model = new DataGridModel<Record>()
            {

                @Override
                public List<Record> fetchData(int first, int pageSize, String sortField, String sortOrder) {
                    String userguid = UserSession.getInstance().getUserGuid();

                    String condition = "";
                    // 审核状态
                    if (StringUtil.isNotBlank(status) && !status.equals("所有")) {
                        condition += " and p.auditstatus='" + status + "' ";
                    }
                    //备案号
                    if (StringUtil.isNotBlank(dataBean.getRecordnum())) {
                        condition += " and zl.recordnum like '%" + dataBean.getRecordnum() + "%' ";
                    }
                    if (StringUtil.isNotBlank(dataBean.getRecordunit())) {
                        condition += " and zl.Recordunit like '%" + dataBean.getRecordunit() + "%' ";
                    }

                    // 如果是企业端，初始化当前登录用户所属企业信息
                    /*if ("0".equals(ConfigUtil.getConfigValue("isframegl"))) {
                        condition += " and  insertuserguid='" + userguid + "' ";
                    }
                    else {
                        condition += " and  auditstatus<>'0' ";
                    }*/
                    List<Record> list = safeprojectservice.fildSafeProjectInfoList(Sql_Table_Name, first, pageSize,
                            condition);
                    List<Record> listall = safeprojectservice.fildSafeProjectInfoList(Sql_Table_Name, condition);

                    for (Record record : list) {
                        // 查询代办信息是否在该用户下
                        Record workflowWorkItem = safeprojectservice.fildWorkflowWorkItemInfo(record.get("pviguid"),
                                userguid);
                        if (workflowWorkItem == null) {
                            record.set("showButton", "0");// 用于查看按钮显示
                        }
                        else {
                            // 处理待办地址，list中的handleurl是从view_workflow_workitem获得的是根据人员guid分组合并后获得的，所以当多个活动拥有相同一个处理人时，list中的handlerurl就不准确了
                            record.set("handleurl", workflowWorkItem.get("handleurl"));
                            record.set("showButton", "1");// 用于修改按钮显示
                        }
                        // 根据fafjguid获取附件guid
                        String fafjguid = record.get("fafjguid");
                        if (StringUtil.isNotBlank(fafjguid)) {
                            List<FrameAttachInfo> FrameList = attachService.getAttachInfoListByGuid(fafjguid);
                            if (StringUtil.isNotBlank(FrameList) && FrameList.size() > 0) {
                                record.set("attachguid", FrameList.get(0).getAttachGuid());
                            }
                        }
                        // 查附件guid代码止
                        // 企业端未提交状态
                        String status = record.get("auditstatus");
                        record.put("status", status);
                        if (StringUtil.isNotBlank(status)) {
                            String auditStatusName = "";
                            auditStatusName = CommonZLAQ.shenhe(status, record.get("pviguid"));
                            record.put("auditstatus", auditStatusName);
                        }
                    }
                    if (StringUtil.isNotBlank(listall)) {
                        this.setRowCount(listall.size());
                    }
                    return list;
                }
            };
        }
        return model;
    }

    // aspose打印的后台
    public void PrintWord() throws IOException {
        HashMap<String, Object> datas = new HashMap<String, Object>();
        String rowguid = request.getParameter("rowguid");
        ZhkPropertyrecord cqba = service.find(rowguid);
        if (cqba != null) {
            datas.put("propertycertificatenum", cqba.getPropertycertificatenum());// 产权登记证编号
            datas.put("registerdate", EpointDateUtil.convertDate2String(cqba.getRegisterdate(), "yyyy年MM月dd日"));// 登记日期
            datas.put("devicename", cqba.getDevicename());// 设备名称
            datas.put("createlicensenum", cqba.getCreatelicensenum()); //制造许可证号
            datas.put("createfactory", cqba.getCreatefactory()); //制造厂家 		
            datas.put("productiondate", EpointDateUtil.convertDate2String(cqba.getProductiondate(), "yyyy年MM月dd日"));// 出厂日期
            datas.put("specificationnum", cqba.getSpecificationnum());// 规格型号
            datas.put("productionnum", cqba.getProductionnum()); //出厂编号
            datas.put("deviceowner", cqba.getDeviceowner()); //设备产权人
            datas.put("telephone", cqba.getTelephone()); //联系电话			
        }
        // 模板文件名
        String docName = "建筑起重设备产权登记.doc";
        // 模板路径
        String inPath = (ClassPathUtil.getDeployWarPath() + "WEB-INF/office/word/").replace("/", File.separator)
                + docName;
        // 生成word的名称
        String fileName = "建筑起重设备产权登记" + ".doc";
        // word生成路径
        String outPath = (ClassPathUtil.getDeployWarPath() + "EpointMisTempFile/").replace("/", File.separator)
                + fileName;

        InputStream in = null;
        OutputStream out = null;
        File file = null;
        try {

            // 传入参数，根据模板生成word
            PrintFuction doc = new PrintFuction();

            Document doctp = new Document(inPath);
            doc.replaceDocTem(doctp, outPath, datas);
            // 替换保存后的内容
            doctp.save(outPath);

            // 下载生成的word
            file = new File(outPath);
            in = new FileInputStream(file);

            sendRespose(in, fileName, "");
            in.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (null != in) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != file) {
                file.delete();
            }
            if (null != out) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void getPropertyrecord() {
        String ownerrecordnum = request.getParameter("ownerrecordnum");
        //String sql = "select * from ZhkPropertyrecord where RecordNum = '" + ownerrecordnum + "'";
        ZhkPropertyrecord zhkpropertyrecord = service.findByColumn("Zhk_Propertyrecord", "RecordNum", ownerrecordnum);
        //ZhkPropertyrecord zhkpropertyrecord=service.find(sql, ZhkPropertyrecord.class);
        if (StringUtil.isNotBlank(zhkpropertyrecord)) {
            addCallbackParam("devicename", zhkpropertyrecord.getDevicename());
            addCallbackParam("specificationnum", zhkpropertyrecord.getSpecificationnum());
        }
    }

    public ZhkPropertyrecord getDataBean() {
        if (dataBean == null) {
            dataBean = new ZhkPropertyrecord();
        }
        return dataBean;
    }

    public void setDataBean(ZhkPropertyrecord dataBean) {
        this.dataBean = dataBean;
    }

    public ExportModel getExportModel() {
        if (exportModel == null) {
            exportModel = new ExportModel("", "");
        }
        return exportModel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
