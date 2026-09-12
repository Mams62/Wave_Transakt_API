package com.wavetransakt.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppUpdateController {

    @Value("${wave.app.android.latest-version-code:1}")
    private int latestVersionCode;

    @Value("${wave.app.android.latest-version-name:1.0}")
    private String latestVersionName;

    @Value("${wave.app.android.download-url:}")
    private String downloadUrl;

    @Value("${wave.app.android.force-update:false}")
    private boolean forceUpdate;

    @Value("${wave.app.android.release-notes:}")
    private String releaseNotes;

    @GetMapping("/update")
    public ResponseEntity<AppUpdateResponse> update(
            @RequestParam(name = "versionCode", defaultValue = "0") int currentVersionCode
    ) {
        boolean updateAvailable = latestVersionCode > currentVersionCode && downloadUrl != null && !downloadUrl.isBlank();
        return ResponseEntity.ok(new AppUpdateResponse(
                updateAvailable,
                latestVersionCode,
                latestVersionName,
                updateAvailable ? downloadUrl.trim() : null,
                forceUpdate && updateAvailable,
                releaseNotes == null || releaseNotes.isBlank() ? null : releaseNotes.trim()
        ));
    }

    public record AppUpdateResponse(
            boolean updateAvailable,
            int latestVersionCode,
            String latestVersionName,
            String downloadUrl,
            boolean forceUpdate,
            String releaseNotes
    ) {}
}
