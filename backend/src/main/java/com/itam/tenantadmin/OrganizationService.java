package com.itam.tenantadmin;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.security.TenantContext;
import com.itam.tenantadmin.dto.CreateOrgRequest;
import com.itam.tenantadmin.dto.OrgNode;
import com.itam.tenantadmin.dto.UpdateOrgRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 组织管理（租户级）。树形结构由平铺记录在内存组装。
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;

    private UUID tenantId() {
        UUID t = TenantContext.getCurrentTenantId();
        if (t == null) throw new BusinessException(ResultCode.NO_PERMISSION, "无租户上下文");
        return t;
    }

    public List<OrgNode> tree() {
        UUID tid = tenantId();
        List<Organization> all = organizationRepository.findByTenantIdOrderBySortAsc(tid);
        Map<UUID, OrgNode> nodeMap = new LinkedHashMap<>();
        for (Organization o : all) {
            nodeMap.put(o.getId(), new OrgNode(o.getId(), o.getName(), o.getCode(),
                    o.getType(), o.getSort(), o.getStatus(), new ArrayList<>()));
        }
        List<OrgNode> roots = new ArrayList<>();
        for (Organization o : all) {
            OrgNode node = nodeMap.get(o.getId());
            if (o.getParentId() == null) {
                roots.add(node);
            } else {
                OrgNode parent = nodeMap.get(o.getParentId());
                if (parent != null) parent.children().add(node);
                else roots.add(node);
            }
        }
        return roots;
    }

    @Transactional
    public OrgNode create(CreateOrgRequest req) {
        UUID tid = tenantId();
        if (organizationRepository.existsByTenantIdAndCode(tid, req.code())) {
            throw new BusinessException(ResultCode.CONFLICT, "组织编码已存在");
        }
        if (req.parentId() != null
                && organizationRepository.findByTenantIdAndId(tid, req.parentId()).isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "父组织不存在或不属于当前租户");
        }
        Organization o = new Organization();
        o.setTenantId(tid);
        o.setParentId(req.parentId());
        o.setName(req.name());
        o.setCode(req.code());
        o.setType(req.type());
        o.setSort(req.sort());
        o.setStatus("ACTIVE");
        o.setCreatedBy(TenantContext.getCurrentUserId());
        o.setUpdatedBy(TenantContext.getCurrentUserId());
        o = organizationRepository.save(o);
        auditLogService.log("ORG_CREATE", "ORGANIZATION", o.getId().toString(), Map.of("code", req.code()));
        return toNode(o);
    }

    @Transactional
    public OrgNode update(UUID id, UpdateOrgRequest req) {
        UUID tid = tenantId();
        Organization o = organizationRepository.findByTenantIdAndId(tid, id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "组织不存在"));
        if (req.parentId() != null) {
            if (req.parentId().equals(id)) throw new BusinessException(ResultCode.PARAM_ERROR, "不能挂到自身");
            if (organizationRepository.findByTenantIdAndId(tid, req.parentId()).isEmpty()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "父组织不存在或不属于当前租户");
            }
        }
        o.setParentId(req.parentId());
        if (req.name() != null) o.setName(req.name());
        if (req.code() != null) o.setCode(req.code());
        if (req.type() != null) o.setType(req.type());
        if (req.sort() != null) o.setSort(req.sort());
        if (req.status() != null) o.setStatus(req.status());
        o.setUpdatedBy(TenantContext.getCurrentUserId());
        o = organizationRepository.save(o);
        auditLogService.log("ORG_UPDATE", "ORGANIZATION", id.toString(), null);
        return toNode(o);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tid = tenantId();
        Organization o = organizationRepository.findByTenantIdAndId(tid, id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "组织不存在"));
        o.setDeleted(true);
        o.setUpdatedBy(TenantContext.getCurrentUserId());
        organizationRepository.save(o);
        auditLogService.log("ORG_DELETE", "ORGANIZATION", id.toString(), null);
    }

    private OrgNode toNode(Organization o) {
        return new OrgNode(o.getId(), o.getName(), o.getCode(), o.getType(), o.getSort(), o.getStatus(), new ArrayList<>());
    }
}
