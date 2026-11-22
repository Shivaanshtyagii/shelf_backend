package com.shelf.assets.internal;

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

    @GetMapping("/api/capabilities")
    Capabilities capabilities() { return new Capabilities(assets.configured(), false); }

    @PostMapping(value = "/api/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AssetService.Uploaded upload(Principal principal, @RequestParam Assets.Kind kind,
                                 @RequestPart MultipartFile file) throws IOException {
        return assets.upload(accounts.verified(principal).id(), kind, file);
    }

    record Capabilities(boolean assets, boolean digitalCommerce) {}
}
