--[[
	Heroes
	======
	Every hero's stats, weapon and abilities. The server uses these numbers to run the abilities
	(see ServerScriptService/Server/HeroKits) and the client uses them for the HUD and the hero
	select screen, so changing a number here changes it everywhere.

	HOW TO ADD A HERO
	  1. Copy one of the entries below, give it a new Id and change the numbers.
	  2. Add the Id to Heroes.Order so it appears on the hero select screen.
	  3. Create ServerScriptService/Server/HeroKits/<Id>.luau with Ability1, Ability2 and
	     Ultimate functions (copy an existing kit to start).

	Primary weapon kinds:
	  "Hitscan"     instant bullets (rifles, shotguns). Pellets > 1 makes a shotgun.
	  "Projectile"  travelling shots. SplashRadius/SplashDamage make them explode,
	                Heal makes them heal the allies they hit.
	  "Melee"       hits everything in a cone in front of you.

	The "Bot" table tells bots how to use each ability:
	  Engage       dash at a nearby enemy          Attack       use on a visible enemy
	  AttackClose  use when an enemy is in reach   Escape       use when low on health
	  Travel       use to reach the objective      Defend       place cover toward enemies
	  HealAllies   use when a nearby ally is hurt  TeamUlt      use when allies are fighting nearby
]]

local Heroes = {}

Heroes.Roles = {
	Tank = { Name = "TANK", Icon = "■", Color = Color3.fromRGB(110, 170, 255) },
	Damage = { Name = "DAMAGE", Icon = "▲", Color = Color3.fromRGB(255, 125, 105) },
	Support = { Name = "SUPPORT", Icon = "✚", Color = Color3.fromRGB(120, 230, 150) },
}

Heroes.Definitions = {
	----------------------------------------------------------------------------
	Bulwark = {
		Id = "Bulwark",
		DisplayName = "Bulwark",
		Role = "Tank",
		Tagline = "The wall that walks.",
		Description = "A heavy defender who shields the team with energy walls and smashes through enemy lines.",
		Color = Color3.fromRGB(60, 110, 170),
		AccentColor = Color3.fromRGB(110, 225, 255),
		MaxHealth = 600,
		WalkSpeed = 14.5,
		JumpHeight = 6.5,
		UltCost = 2200, -- damage + healing needed to charge the ultimate
		Primary = {
			Name = "Breach Cannon",
			Description = "Close-range scatter shot. Devastating up close, weak at range.",
			Kind = "Hitscan",
			Damage = 9, -- per pellet
			Pellets = 9,
			SpreadDegrees = 5.5,
			FireRate = 1.2, -- shots per second
			Automatic = true,
			Range = 45,
			FalloffStart = 12,
			FalloffEnd = 35,
			FalloffMin = 0.35,
			HeadshotMultiplier = 1.5,
			MagazineSize = 6,
			ReloadTime = 2,
			TracerColor = Color3.fromRGB(110, 225, 255),
			TracerWidth = 0.12,
		},
		Abilities = {
			Ability1 = {
				Name = "Ram Dash",
				Icon = "»",
				Description = "Charge forward, damaging and knocking back enemies in your path.",
				Cooldown = 8,
				Distance = 30,
				Duration = 0.4,
				Damage = 45,
				Knockback = 55,
				HitRadius = 6,
			},
			Ability2 = {
				Name = "Aegis Wall",
				Icon = "▮",
				Description = "Deploy an energy wall that blocks enemy fire. Your team can shoot through it.",
				Cooldown = 14,
				Health = 900,
				Duration = 9,
				Width = 16,
				Height = 9,
				PlaceDistance = 9,
			},
			Ultimate = {
				Name = "Fortress Protocol",
				Icon = "✦",
				Description = "You and nearby allies gain 200 shields and take 30% less damage for 8 seconds.",
				Radius = 30,
				Shield = 200,
				DamageReduction = 0.3,
				Duration = 8,
			},
		},
		Bot = { PreferredRange = 14, Ability1 = "Engage", Ability2 = "Defend", Ultimate = "TeamUlt" },
	},

	----------------------------------------------------------------------------
	Kestrel = {
		Id = "Kestrel",
		DisplayName = "Kestrel",
		Role = "Damage",
		Tagline = "Eyes in the sky.",
		Description = "An agile marksman with a jet harness who takes the high ground and picks targets off from range.",
		Color = Color3.fromRGB(200, 120, 40),
		AccentColor = Color3.fromRGB(255, 190, 70),
		MaxHealth = 250,
		WalkSpeed = 16.5,
		JumpHeight = 7.5,
		UltCost = 2000,
		Primary = {
			Name = "Talon Rifle",
			Description = "Fully automatic rifle. Headshots deal 80% bonus damage.",
			Kind = "Hitscan",
			Damage = 19,
			FireRate = 7,
			Automatic = true,
			Range = 160,
			SpreadDegrees = 0.8,
			FalloffStart = 45,
			FalloffEnd = 100,
			FalloffMin = 0.6,
			HeadshotMultiplier = 1.8,
			MagazineSize = 30,
			ReloadTime = 1.5,
			TracerColor = Color3.fromRGB(255, 190, 70),
			TracerWidth = 0.08,
		},
		Abilities = {
			Ability1 = {
				Name = "Jet Leap",
				Icon = "▲",
				Description = "Blast up and forward with your jet harness.",
				Cooldown = 7,
				UpSpeed = 60,
				ForwardSpeed = 40,
				Duration = 0.22,
			},
			Ability2 = {
				Name = "Shock Grenade",
				Icon = "●",
				Description = "Lob a grenade that explodes, damaging and slowing enemies.",
				Cooldown = 10,
				Damage = 70,
				Radius = 12,
				Speed = 85,
				Gravity = 110,
				SlowAmount = 0.4,
				SlowDuration = 2.5,
			},
			Ultimate = {
				Name = "Overdrive",
				Icon = "✦",
				Description = "For 8 seconds: fire 50% faster, deal 25% more damage and never reload.",
				Duration = 8,
				FireRateMult = 1.5,
				DamageMult = 1.25,
			},
		},
		Bot = { PreferredRange = 40, Ability1 = "Escape", Ability2 = "Attack", Ultimate = "Attack" },
	},

	----------------------------------------------------------------------------
	Volta = {
		Id = "Volta",
		DisplayName = "Volta",
		Role = "Damage",
		Tagline = "Lightning never strikes once.",
		Description = "A storm-caller who fires exploding arc orbs and controls space with electric fields.",
		Color = Color3.fromRGB(110, 60, 190),
		AccentColor = Color3.fromRGB(190, 130, 255),
		MaxHealth = 250,
		WalkSpeed = 16,
		JumpHeight = 7.2,
		UltCost = 2300,
		Primary = {
			Name = "Arc Orbs",
			Description = "Exploding orbs: a direct hit plus splash damage around the impact.",
			Kind = "Projectile",
			Damage = 30,
			SplashDamage = 25,
			SplashRadius = 7,
			ProjectileSpeed = 120,
			ProjectileGravity = 0,
			ProjectileRadius = 0.6,
			FireRate = 1.8,
			Automatic = true,
			Range = 200,
			MagazineSize = 8,
			ReloadTime = 1.8,
			TracerColor = Color3.fromRGB(190, 130, 255),
		},
		Abilities = {
			Ability1 = {
				Name = "Blink",
				Icon = "◆",
				Description = "Teleport a short distance in the direction you are looking.",
				Cooldown = 6,
				Distance = 28,
			},
			Ability2 = {
				Name = "Static Field",
				Icon = "◎",
				Description = "Electrify an area. Enemies inside take damage and are slowed.",
				Cooldown = 12,
				Radius = 13,
				Duration = 4,
				TickDamage = 14,
				TickInterval = 0.5,
				SlowAmount = 0.3,
				MaxRange = 60,
			},
			Ultimate = {
				Name = "Thunderhead",
				Icon = "✦",
				Description = "Call down three lightning strikes on the target area.",
				Radius = 18,
				Strikes = 3,
				StrikeInterval = 0.7,
				StrikeDamage = 85,
				Delay = 1,
				MaxRange = 80,
				Duration = 3.1, -- Delay + Strikes * StrikeInterval
			},
		},
		Bot = { PreferredRange = 28, Ability1 = "Travel", Ability2 = "Attack", Ultimate = "Attack" },
	},

	----------------------------------------------------------------------------
	Kaida = {
		Id = "Kaida",
		DisplayName = "Kaida",
		Role = "Damage",
		Tagline = "Strike first. Strike twice.",
		Description = "A blade dancer who dives the back line. Her dash resets whenever it lands a kill.",
		Color = Color3.fromRGB(170, 40, 70),
		AccentColor = Color3.fromRGB(255, 90, 140),
		MaxHealth = 300,
		WalkSpeed = 18,
		JumpHeight = 8,
		UltCost = 1900,
		Primary = {
			Name = "Twin Fangs",
			Description = "Fast blade combo that hits everything in front of you.",
			Kind = "Melee",
			Damage = 40,
			Range = 10,
			ArcDegrees = 110,
			FireRate = 2.2,
			Automatic = true,
			TracerColor = Color3.fromRGB(255, 90, 140),
		},
		Abilities = {
			Ability1 = {
				Name = "Phantom Dash",
				Icon = "»",
				Description = "Dash forward, slicing enemies you pass through. Resets on a kill.",
				Cooldown = 7,
				Distance = 32,
				Duration = 0.3,
				Damage = 50,
				HitRadius = 6,
			},
			Ability2 = {
				Name = "Guard Stance",
				Icon = "◈",
				Description = "Brace for 1.5 seconds, taking 75% less damage, then slash everything around you.",
				Cooldown = 10,
				Duration = 1.5,
				DamageReduction = 0.75,
				CounterDamage = 60,
				CounterRadius = 12,
			},
			Ultimate = {
				Name = "Blade Tempest",
				Icon = "✦",
				Description = "Become a whirlwind of blades for 4 seconds, shredding nearby enemies.",
				Duration = 4,
				Radius = 13,
				TickDamage = 30,
				TickInterval = 0.25,
				SpeedBoost = 0.35,
			},
		},
		Bot = { PreferredRange = 6, Ability1 = "Engage", Ability2 = "AttackClose", Ultimate = "AttackClose" },
	},

	----------------------------------------------------------------------------
	Lumen = {
		Id = "Lumen",
		DisplayName = "Lumen",
		Role = "Support",
		Tagline = "No one falls on my watch.",
		Description = "A light-weaver who heals allies with mending bolts and turns fights with a sanctuary of light.",
		Color = Color3.fromRGB(210, 170, 60),
		AccentColor = Color3.fromRGB(255, 235, 140),
		MaxHealth = 275,
		WalkSpeed = 16,
		JumpHeight = 7.2,
		UltCost = 2400,
		Primary = {
			Name = "Mending Bolts",
			Description = "Bolts heal the allies they hit and damage enemies.",
			Kind = "Projectile",
			Damage = 18,
			Heal = 30,
			ProjectileSpeed = 150,
			ProjectileGravity = 0,
			ProjectileRadius = 0.9,
			FireRate = 3,
			Automatic = true,
			Range = 150,
			MagazineSize = 20,
			ReloadTime = 1.6,
			TracerColor = Color3.fromRGB(255, 235, 140),
		},
		Abilities = {
			Ability1 = {
				Name = "Radiant Pulse",
				Icon = "✚",
				Description = "Instantly heal yourself and nearby allies.",
				Cooldown = 9,
				Radius = 20,
				Heal = 80,
			},
			Ability2 = {
				Name = "Tailwind",
				Icon = "»",
				Description = "You and nearby allies move 30% faster for 4 seconds.",
				Cooldown = 14,
				Radius = 22,
				SpeedBoost = 0.3,
				Duration = 4,
			},
			Ultimate = {
				Name = "Sanctuary",
				Icon = "✦",
				Description = "Create a zone of light that rapidly heals allies and reduces the damage they take.",
				Radius = 20,
				Duration = 7,
				HealPerSecond = 55,
				DamageReduction = 0.2,
			},
		},
		Bot = { PreferredRange = 26, Ability1 = "HealAllies", Ability2 = "Travel", Ultimate = "TeamUlt" },
	},
}

-- Order heroes appear on the hero select screen.
Heroes.Order = { "Bulwark", "Kestrel", "Volta", "Kaida", "Lumen" }

-- The three ability slots every hero has, in HUD order.
Heroes.AbilitySlots = { "Ability1", "Ability2", "Ultimate" }

function Heroes.Get(id)
	if typeof(id) ~= "string" then
		return nil
	end
	return Heroes.Definitions[id]
end

function Heroes.GetOrdered()
	local list = {}
	for _, id in Heroes.Order do
		local hero = Heroes.Definitions[id]
		if hero then
			table.insert(list, hero)
		end
	end
	return list
end

return Heroes
