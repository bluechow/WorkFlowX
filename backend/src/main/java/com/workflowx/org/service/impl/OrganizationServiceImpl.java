package com.workflowx.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.dto.UpdateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.entity.OrganizationMember;
import com.workflowx.org.entity.OrgMemberType;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.mapper.OrganizationMemberMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.org.vo.OrganizationMemberVO;
import com.workflowx.org.vo.OrganizationVO;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 组织领域服务实现（P4-01）。
 * 异常约定: 缺失 404 / 唯一冲突 409 / 系统保护与业务规则 400 / 越权 403（ForbiddenException）。
 */
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper memberMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public OrganizationVO create(CreateOrganizationRequest request, Long operatorId) {
        Organization org = new Organization();
        org.setName(request.name());
        org.setCode(request.code());
        org.setOwnerId(operatorId);
        org.setDescription(request.description());
        try {
            organizationMapper.insert(org);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "组织 code 已存在: " + request.code());
        }
        // 创建者即 OWNER（同事务写入成员关系）
        memberMapper.insert(org.getId(), operatorId, OrgMemberType.OWNER.name());
        return OrganizationVO.from(requireOrg(org.getId()));
    }

    @Override
    public OrganizationVO getById(Long id) {
        return OrganizationVO.from(requireOrg(id));
    }

    @Override
    public PageVO<OrganizationVO> page(String keyword, int page, int size) {
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<Organization>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Organization::getName, keyword)
                    .or().like(Organization::getCode, keyword));
        }
        wrapper.orderByDesc(Organization::getCreatedAt).orderByDesc(Organization::getId);
        Page<Organization> result = organizationMapper.selectPage(new Page<>(page, size), wrapper);
        return PageVO.of(result.convert(OrganizationVO::from));
    }

    @Override
    @Transactional
    public OrganizationVO update(Long id, UpdateOrganizationRequest request) {
        Organization org = requireOrg(id);
        if (request.name() != null) {
            org.setName(request.name());
        }
        if (request.description() != null) {
            org.setDescription(request.description());
        }
        organizationMapper.updateById(org);
        return OrganizationVO.from(requireOrg(id));
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        Organization org = requireOrg(id);
        if (!org.getOwnerId().equals(operatorId)) {
            throw new ForbiddenException("仅组织所有者可删除组织");
        }
        // departments / organization_members 经 FK CASCADE 级联清理
        organizationMapper.deleteById(id);
    }

    @Override
    @Transactional
    public OrganizationMemberVO addMember(Long orgId, AddOrganizationMemberRequest request) {
        requireOrg(orgId);
        if (userMapper.selectById(request.userId()) == null) {
            throw new ResourceNotFoundException("user", request.userId());
        }
        if (memberMapper.findMember(orgId, request.userId()) != null) {
            throw new BusinessException(409, "用户已是组织成员");
        }
        String role = StringUtils.hasText(request.role()) ? request.role() : OrgMemberType.MEMBER.name();
        try {
            memberMapper.insert(orgId, request.userId(), role);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户已是组织成员");
        }
        return OrganizationMemberVO.from(requireMember(orgId, request.userId()));
    }

    @Override
    @Transactional
    public void removeMember(Long orgId, Long userId) {
        OrganizationMember member = requireMember(orgId, userId);
        if (member.getRole() == OrgMemberType.OWNER) {
            throw new BusinessException(400, "组织所有者不可移除");
        }
        memberMapper.deleteMember(orgId, userId);
    }

    @Override
    public List<OrganizationMemberVO> listMembers(Long orgId) {
        requireOrg(orgId);
        return memberMapper.findMembersByOrgId(orgId).stream()
                .map(OrganizationMemberVO::from).toList();
    }

    private Organization requireOrg(Long id) {
        Organization org = organizationMapper.selectById(id);
        if (org == null) {
            throw new ResourceNotFoundException("organization", id);
        }
        return org;
    }

    private OrganizationMember requireMember(Long orgId, Long userId) {
        OrganizationMember member = memberMapper.findMember(orgId, userId);
        if (member == null) {
            throw new ResourceNotFoundException("organization member", "org=" + orgId + ", user=" + userId);
        }
        return member;
    }
}
