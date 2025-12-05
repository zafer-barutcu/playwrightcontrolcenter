Feature: SDK quickstarts
  Language-specific onboarding smoke checks.

@sdk @java
  Scenario: Java quickstart page LANG01
    Given "/java/docs/intro" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

@sdk @python
  Scenario: Python quickstart page LANG02
    Given "/python/docs/intro" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

@sdk @dotnet
  Scenario: .NET quickstart page LANG03
    Given "/dotnet/docs/intro" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

@sdk @api
  Scenario: API class reference LANG04
    Given "/docs/api/class-page" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

@sdk @trace
  Scenario: Trace viewer language doc LANG05
    Given "/docs/trace-viewer" adresine giderim
    Then sayfa basligi "Playwright" icermelidir
