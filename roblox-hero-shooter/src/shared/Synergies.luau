--[[
	Synergies (team-ups)
	====================
	When every hero listed in a synergy is on the same team, each of those heroes gets its bonus.
	The HUD shows active synergies, and the hero select screen lists them so players can plan.

	Supported bonus stats:
	  DamageMult, HealingMult, CooldownMult, ProjectileSpeedMult, BarrierHealthMult
	      multipliers: 1.1 = +10%, 0.85 = -15%
	  MaxHealthBonus
	      extra maximum health (added when the hero spawns)
]]

local Synergies = {}

Synergies.List = {
	{
		Id = "RadiantShield",
		Name = "Radiant Shield",
		Heroes = { "Bulwark", "Lumen" },
		Description = "Bulwark's Aegis Wall is 35% tougher and Lumen heals 15% more.",
		Bonuses = {
			Bulwark = { BarrierHealthMult = 1.35 },
			Lumen = { HealingMult = 1.15 },
		},
	},
	{
		Id = "StormCell",
		Name = "Storm Cell",
		Heroes = { "Kestrel", "Volta" },
		Description = "Kestrel and Volta deal 10% more damage, and Volta's orbs fly 20% faster.",
		Bonuses = {
			Kestrel = { DamageMult = 1.1 },
			Volta = { DamageMult = 1.1, ProjectileSpeedMult = 1.2 },
		},
	},
	{
		Id = "GuidingLight",
		Name = "Guiding Light",
		Heroes = { "Kaida", "Lumen" },
		Description = "Kaida gains 50 max health and Lumen's abilities recharge 15% faster.",
		Bonuses = {
			Kaida = { MaxHealthBonus = 50 },
			Lumen = { CooldownMult = 0.85 },
		},
	},
	{
		Id = "IronAndEdge",
		Name = "Iron & Edge",
		Heroes = { "Bulwark", "Kaida" },
		Description = "Bulwark and Kaida recharge their abilities 15% faster.",
		Bonuses = {
			Bulwark = { CooldownMult = 0.85 },
			Kaida = { CooldownMult = 0.85 },
		},
	},
}

-- Every synergy a hero is part of (used by the hero select screen).
function Synergies.ForHero(heroId)
	local results = {}
	for _, synergy in Synergies.List do
		if table.find(synergy.Heroes, heroId) then
			table.insert(results, synergy)
		end
	end
	return results
end

return Synergies
