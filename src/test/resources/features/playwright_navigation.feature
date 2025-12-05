Feature: Customer journey pages
  End-to-end landing and product navigation smoke checks.

  @journey @smoke
  Scenario: Homepage hero renders NAV01
    Given "/" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey
  Scenario: Getting started checklist NAV02
    Given "/docs/intro" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey
  Scenario: Why Playwright decision page NAV03
    Given "/docs/why-playwright" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey @browsers
  Scenario: Browsers compatibility page NAV04
    Given "/docs/browsers" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey @runners
  Scenario: Test runners overview NAV05
    Given "/docs/test-runners" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey @api
  Scenario: API class reference NAV06
    Given "/docs/api/class-page" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey @config
  Scenario: Test configuration guide NAV07
    Given "/docs/test-configuration" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @journey @trace
  Scenario: Trace viewer walkthrough NAV08
    Given "/docs/trace-viewer" adresine giderim
    Then sayfa basligi "Playwright" icermelidir
