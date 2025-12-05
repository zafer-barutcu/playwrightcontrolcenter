package com.zaferbarutcu.steps;

import com.google.inject.Inject;
import com.zaferbarutcu.config.TestConfig;
import com.zaferbarutcu.pages.NavigationPage;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.junit.jupiter.api.Assertions;

@ScenarioScoped
public class PlaywrightSteps {

    private final NavigationPage navigationPage;
    private final TestConfig config;

    @Inject
    public PlaywrightSteps(NavigationPage navigationPage, TestConfig config) {
        this.navigationPage = navigationPage;
        this.config = config;
    }

    @Given("Playwright ana sayfasini acarim")
    public void openHomePage() {
        navigationPage.navigate("/");
    }

    @Given("{string} adresine giderim")
    public void goToAddress(String pathOrUrl) {
        navigationPage.navigate(pathOrUrl);
    }

    @Then("sayfa basligi {string} icermelidir")
    public void assertTitleContains(String expected) {
        String title = navigationPage.title();
        Assertions.assertTrue(
                title.contains(expected),
                String.format("Title '%s' does not contain expected text '%s'", title, expected)
        );
    }

    @Then("sayfa basligi varsayilan anahtar kelimeyi icermelidir")
    public void assertTitleContainsDefault() {
        String title = navigationPage.title();
        Assertions.assertTrue(
                title.toLowerCase().contains("playwright"),
                String.format("Title '%s' expected to contain 'playwright'", title)
        );
    }
}
