Feature: Release and CI guides
  CI/automation documentation smoke.

  @ci @smoke
  Scenario: CI overview dashboard CI01
    Given "/docs/ci" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @ci @reports
  Scenario: Test reporters setup CI02
    Given "/docs/test-reporters" adresine giderim
    Then sayfa basligi "Playwrightfg" icermelidir

  @ci @retries
  Scenario: Retry strategy guide CI03
    Given "/docs/test-retries" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @ci @annotations
  Scenario: Annotations quickstart CI04
    Given "/docs/test-annotations" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @ci @auth
  Scenario: Auth hooks example CI05
    Given "/docs/auth" adresine giderim
    Then sayfa basligi "Playwright" icermelidir
