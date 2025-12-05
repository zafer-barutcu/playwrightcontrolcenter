Feature: Cross-site smoke pack

  @docs @smoke
  Scenario: Playwright docs landing TOOL01
    Given "/" adresine giderim
    Then sayfa basligi "Playwright" icermelidir

  @google @test1
  Scenario: Google Test Demo Scenarios TOOL02
    Given "https://www.google.com/" adresine giderim
    Then sayfa basligi "Google" icermelidir

  @trace @smoke
  Scenario: Trace viewer deep link TOOL03
    Given "/docs/trace-viewer" adresine giderim
    Then sayfa basligi "Playwright" icermelidir
