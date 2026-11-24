package com.shelf.api.assets.web;

import com.shelf.api.assets.api.Assets;
import com.shelf.api.assets.api.UploadedAssetResponse;
import com.shelf.api.assets.service.AssetService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;

@RestController
class AssetController {
    private final AssetService assets;

    AssetController(AssetService assets) {
        this.assets = assets;
    }

    @PostMapping(value = "/api/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadedAssetResponse upload(Principal principal, @RequestParam Assets.Kind kind,
                                 @RequestPart MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return assets.upload(principal, kind, input, file.getSize());
        }
    }
}
