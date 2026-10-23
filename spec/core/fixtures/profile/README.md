# Profile fixtures

`/api/oauth/profile` responses and the usage endpoint's Free-plan refusal, for
`PlanTag.parse`/`PlanTag.isPlanRefusal` (CCM-68 [Accounts], CCM-67 [Readout]). See
`API-CONTRACT.md` §9 for the schema and `BEHAVIOR-SPEC.md` §9–§10 for the rules these back.

- `free.json` — `organization_type: "claude_free"` → plan `"Free"`, `usageBlocked == true`.
- `pro.json` — `organization_type: "claude_pro"` → plan `"Pro"`.
- `max.json` — `organization_type: "claude_max"`, tier `default_claude_max_20x` → plan
  `"Max"`, multiplier `"20x"`.
- `team-standard.json` — `organization_type: "claude_team"`, `seat_tier: "team_standard"` →
  plan `"Team Standard"`.
- `team-premium.json` — `organization_type: "claude_team"`, `seat_tier: "team_tier_1"` (the
  premium seat, named oddly on purpose — Anthropic renames it) → plan `"Team Premium"`.
- `enterprise.json` — `organization_type: "claude_enterprise"` → plan `"Enterprise"`.
- `403-plan-refusal.json` — the usage endpoint's HTTP 403 body for a Free organisation;
  `PlanTag.isPlanRefusal` matches on `error.details.error_code ==
  "oauth_not_allowed_for_organization"`.
