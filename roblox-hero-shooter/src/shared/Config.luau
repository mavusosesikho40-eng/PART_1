--[[
	Config
	======
	Every number that controls how a match plays lives here, so you can tune the game without
	digging through the code. Hero stats live in Heroes.luau, team-ups in Synergies.luau.

	This module is shared: the server and every client read the same values.
]]

local Config = {}

-- Shown on menus and banners. Rename it to whatever you call your game!
Config.GameName = "RIFT ARENA"

--------------------------------------------------------------------------------
-- Teams (exactly two). "Name" is used by the code; the colours are used by the HUD.
--------------------------------------------------------------------------------
Config.Teams = {
	{
		Name = "Blue",
		DisplayName = "BLUE",
		BrickColor = BrickColor.new("Bright blue"),
		Color = Color3.fromRGB(70, 160, 255),
	},
	{
		Name = "Red",
		DisplayName = "RED",
		BrickColor = BrickColor.new("Bright red"),
		Color = Color3.fromRGB(255, 80, 80),
	},
}

function Config.GetTeam(name)
	for _, team in Config.Teams do
		if team.Name == name then
			return team
		end
	end
	return nil
end

function Config.GetTeamColor(name): Color3
	local team = Config.GetTeam(name)
	return if team then team.Color else Color3.fromRGB(210, 210, 215)
end

function Config.GetOtherTeam(name)
	if name == Config.Teams[1].Name then
		return Config.Teams[2].Name
	elseif name == Config.Teams[2].Name then
		return Config.Teams[1].Name
	end
	return nil
end

--------------------------------------------------------------------------------
-- Match flow
--------------------------------------------------------------------------------
Config.Match = {
	MaxPlayersPerTeam = 6, -- real players per team
	BotsFillTeamsTo = 5, -- top each team up to this many fighters with bots (0 = no bots)
	MinPlayersToStart = 1, -- real players needed before a match begins
	IntermissionTime = 10, -- seconds between matches
	SetupTime = 20, -- start of every round: pick heroes, spawn doors stay shut
	PointUnlockDelay = 10, -- seconds after the doors open before the point can be captured
	RoundsToWin = 2, -- best of 3
	RoundEndTime = 6,
	MatchEndTime = 10,
	RespawnTime = 6,
	AllowDuplicateHeroes = true, -- false = each hero can only be picked once per team
	AssistWindow = 10, -- damage dealt within this many seconds counts toward kills and assists
	SpawnRoomHealPerSecond = 150, -- your own spawn room heals you quickly
	EnemySpawnRoomDamagePerSecond = 200, -- the enemy's spawn room is deadly
	KillHeight = -60, -- fall below this Y and you are eliminated
}

--------------------------------------------------------------------------------
-- Control point ("king of the hill")
--------------------------------------------------------------------------------
Config.Objective = {
	CaptureTime = 8, -- seconds for ONE player to capture the point
	CaptureSpeedPerExtraPlayer = 0.3, -- each extra player on the point adds 30% capture speed...
	MaxCapturers = 3, -- ...up to this many players
	ScorePerSecond = 1.25, -- the holding team gains this many % per second (80s to win a round)
	DecayPerSecond = 10, -- unattended capture progress drains this fast
}

--------------------------------------------------------------------------------
-- Combat
--------------------------------------------------------------------------------
Config.Combat = {
	PassiveUltChargePerSecond = 0.35, -- % of your ultimate gained per second just for being alive
	DefaultHeadshotMultiplier = 1.5,
	MaxShield = 500, -- cap on temporary bonus shields
	FriendlyFire = false,
}

--------------------------------------------------------------------------------
-- Bots (they fill empty slots so you can play alone or with a few friends)
--------------------------------------------------------------------------------
Config.Bots = {
	Names = { "Echo", "Nyx", "Pike", "Juno", "Vale", "Onyx", "Wren", "Sable", "Flint", "Moss", "Rune", "Ash" },
	AimErrorDegrees = 3.5, -- bigger = worse aim
	ReactionTime = 0.45, -- seconds before a bot starts shooting a new target
	SightRange = 140,
	ThinkInterval = 0.1,
	RetreatHealthFraction = 0.3, -- below this much health, bots run home to heal
}

--------------------------------------------------------------------------------
-- Controls. Keyboard + mouse and gamepad; phones and tablets get on-screen buttons.
--------------------------------------------------------------------------------
Config.Controls = {
	Fire = { Enum.UserInputType.MouseButton1, Enum.KeyCode.ButtonR2 },
	Ability1 = { Enum.KeyCode.LeftShift, Enum.KeyCode.ButtonL1 },
	Ability2 = { Enum.KeyCode.E, Enum.KeyCode.ButtonR1 },
	Ultimate = { Enum.KeyCode.Q, Enum.KeyCode.ButtonY },
	Reload = { Enum.KeyCode.R, Enum.KeyCode.ButtonX },
	HeroSelect = { Enum.KeyCode.H, Enum.KeyCode.DPadUp },
	Scoreboard = { Enum.KeyCode.Tab, Enum.KeyCode.ButtonSelect },
}

-- Labels the HUD shows next to each action.
Config.KeyLabels = {
	Fire = "LMB",
	Ability1 = "SHIFT",
	Ability2 = "E",
	Ultimate = "Q",
	Reload = "R",
	HeroSelect = "H",
	Scoreboard = "TAB",
}

--------------------------------------------------------------------------------
-- Camera (over-the-shoulder third person)
--------------------------------------------------------------------------------
Config.Camera = {
	Distance = 11,
	ShoulderOffset = 2.4, -- studs to the right of your hero
	Height = 2.2, -- studs above the HumanoidRootPart
	FieldOfView = 72,
	MouseSensitivity = 0.0032, -- radians per pixel, multiplied by the player's Roblox sensitivity setting
	GamepadSensitivity = 3.2, -- radians per second with the stick fully pushed
	TouchSensitivity = 0.0065,
	MinPitch = -70, -- degrees
	MaxPitch = 70,
}

--------------------------------------------------------------------------------
-- Sounds. These are built-in Roblox sounds that ship with every client. Replace them with
-- your own "rbxassetid://123456" ids to give your game its own voice.
--------------------------------------------------------------------------------
Config.Sounds = {
	Hitscan = "rbxasset://sounds/paintball.wav",
	Projectile = "rbxasset://sounds/Rocket shot.wav",
	Melee = "rbxasset://sounds/swordslash.wav",
	Explosion = "rbxasset://sounds/collide.wav",
	Ability = "rbxasset://sounds/electronicpingshort.wav",
	Dash = "rbxasset://sounds/swordlunge.wav",
	Ultimate = "rbxasset://sounds/unsheath.wav",
	HitMarker = "rbxasset://sounds/electronicpingshort.wav",
}

return Config
