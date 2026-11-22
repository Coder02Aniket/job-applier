package com.jobapplier.ingestion;

import com.jobapplier.config.AppProperties;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persistent logged-in browser profile (log in manually once, reuse the session). */
@Component
@RequiredArgsConstructor
public class PlaywrightSessionFactory {
    private final AppProperties props;

    public Playwright create() { return Playwright.create(); }

    public BrowserContext open(Playwright pw, String portal) {
        Path dir = Path.of(props.playwright().sessionDir(), portal);
        return pw.chromium().launchPersistentContext(dir,
            new BrowserType.LaunchPersistentContextOptions().setHeadless(props.playwright().headless()));
    }

    /** Human-like random delay between actions. */
    public void pause() {
        var p = props.playwright();
        try { Thread.sleep(ThreadLocalRandom.current().nextInt(p.delayMsMin(), p.delayMsMax())); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
