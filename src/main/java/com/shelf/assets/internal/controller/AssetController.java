package com.shelf.assets.internal.controller;

import com.shelf.assets.internal.model.UploadedAsset;
import com.shelf.assets.internal.service.AssetService;
import com.shelf.accounts.Accounts;
import com.shelf.assets.Assets;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;

@RestController
class AssetController {
    private final Accounts accounts;
    private final AssetService assets;

    AssetController(Accounts accounts, AssetService assets) {
        this.accounts = accounts;
        this.assets = assets;
    }

    @PostMapping(value = "/api/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadedAsset upload(Principal principal, @RequestParam Assets.Kind kind,
                                 @RequestPart MultipartFile file) throws IOException {
        var owner = accounts.verified(principal).id();
        try (var input = file.getInputStream()) {
            return assets.upload(owner, kind, input, file.getSize());
        }
    }
}
