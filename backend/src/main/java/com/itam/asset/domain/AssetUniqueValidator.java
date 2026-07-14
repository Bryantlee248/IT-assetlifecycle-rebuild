package com.itam.asset.domain;

import com.itam.asset.repository.AssetRepository;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 资产唯一性预检（友好 409）。
 * 在写入事务前对 asset_no / serial_no 做 existsBy 检查；
 * 并兜底捕获部分唯一索引导致的 DataIntegrityViolationException，映射为对应冲突码。
 */
@Service
public class AssetUniqueValidator {

    private final AssetRepository assetRepository;

    public AssetUniqueValidator(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public void preCheck(UUID tenantId, String assetNo, String serialNo) {
        if (assetRepository.existsByTenantIdAndAssetNo(tenantId, assetNo)) {
            throw new BusinessException(ResultCode.ASSET_NO_CONFLICT);
        }
        if (serialNo != null && !serialNo.isBlank()
                && assetRepository.existsByTenantIdAndSerialNo(tenantId, serialNo)) {
            throw new BusinessException(ResultCode.SERIAL_NO_CONFLICT);
        }
    }

    public RuntimeException translateConflict(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("asset_no")) {
            return new BusinessException(ResultCode.ASSET_NO_CONFLICT);
        }
        if (msg.contains("serial_no")) {
            return new BusinessException(ResultCode.SERIAL_NO_CONFLICT);
        }
        return ex;
    }
}
