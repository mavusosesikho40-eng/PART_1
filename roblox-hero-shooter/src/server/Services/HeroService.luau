--[[
	HeroService
	===========
	Turns a profile into a living character: loads the player's avatar (or builds a bot body),
	applies the hero's stats and gear, and wires up death handling.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)
local Signal = require(Shared.Signal)

local ProfileService = require(script.Parent.ProfileService)
local StatusService = require(script.Parent.StatusService)
local SynergyService = require(script.Parent.SynergyService)
local CombatService = require(script.Parent.CombatService)
local WeaponService = require(script.Parent.WeaponService)
local MapService = require(script.Parent.MapService)
local BotService = require(script.Parent.BotService)
local HeroVisuals = require(script.Parent.HeroVisuals)

local HeroService = {}

HeroService.Spawned = Signal.new() -- (profile, character)

-- Accessories (hats, hair...) shouldn't block shots or count as hits.
local function prepareDescendant(descendant: Instance)
	if descendant:IsA("BasePart") then
		descendant.CollisionGroup = "Characters"
		if descendant.Parent and descendant.Parent:IsA("Accessory") then
			descendant.CanQuery = false
			descendant.CanTouch = false
			descendant.Massless = true
		end
	end
end

local function applyHero(profile, character: Model, spawnCFrame: CFrame)
	local hero = Heroes.Get(profile.HeroId)
	local humanoid = character:FindFirstChildOfClass("Humanoid") or character:WaitForChild("Humanoid", 5)
	if not hero or not humanoid then
		return false
	end

	character:SetAttribute("CombatantId", profile.Id)
	character:SetAttribute("Team", profile.Team)
	character:SetAttribute("Hero", hero.Id)
	character:SetAttribute("BaseWalkSpeed", hero.WalkSpeed)
	character:SetAttribute("SpawnYaw", CombatUtil.YawFromLook(spawnCFrame.LookVector))
	character:SetAttribute("CD_Ability1", 0)
	character:SetAttribute("CD_Ability2", 0)

	local maxHealth = hero.MaxHealth + SynergyService.GetBonus(profile, "MaxHealthBonus", 0)
	humanoid.MaxHealth = maxHealth
	humanoid.Health = maxHealth
	humanoid.WalkSpeed = hero.WalkSpeed
	humanoid.UseJumpPower = false
	humanoid.JumpHeight = hero.JumpHeight
	humanoid.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None -- we draw our own nameplates

	for _, descendant in character:GetDescendants() do
		prepareDescendant(descendant)
	end
	character.DescendantAdded:Connect(prepareDescendant)

	HeroVisuals.Apply(character, hero, Config.GetTeamColor(profile.Team))
	StatusService.Setup(character, hero.WalkSpeed)
	WeaponService.Setup(character, hero)

	humanoid.Died:Connect(function()
		CombatService.HandleDeath(character)
		StatusService.Clear(character)
		WeaponService.Clear(character)
	end)
	return true
end

-- Spawns (or respawns) a fighter at their team's spawn. Yields while the avatar loads.
function HeroService.Spawn(profile)
	if profile.Spawning or not profile.Team or not Heroes.Get(profile.HeroId) then
		return
	end
	profile.Spawning = true
	profile.RespawnAt = nil
	profile.Folder:SetAttribute("RespawnAt", 0)

	local spawnCFrame = MapService.GetSpawnCFrame(profile.Team)
	local character

	if profile.Player then
		local player = profile.Player
		if player.Parent == Players then
			local ok, err = pcall(function()
				player:LoadCharacter()
			end)
			if not ok then
				warn("[HeroService] LoadCharacter failed: " .. tostring(err))
			end
			character = player.Character
		end
	else
		if profile.Character then
			profile.Character:Destroy()
			profile.Character = nil
		end
		character = BotService.CreateCharacter(profile, Heroes.Get(profile.HeroId))
	end

	-- The player may have left, or the bot removed, while the avatar was loading.
	if not character or ProfileService.Get(profile.Id) ~= profile then
		if character and not profile.Player then
			character:Destroy()
		end
		profile.Spawning = false
		return
	end

	if not applyHero(profile, character, spawnCFrame) then
		profile.Spawning = false
		return
	end
	character:PivotTo(spawnCFrame)
	if not profile.Player then
		character.Parent = workspace
		BotService.OnSpawned(profile, character)
	end

	profile.Character = character
	profile.DeathHandledFor = nil
	profile.Folder:SetAttribute("Alive", true)
	profile.Spawning = false
	HeroService.Spawned:Fire(profile, character)
end

-- Removes a fighter's body (between matches).
function HeroService.Despawn(profile)
	local character = profile.Character
	profile.Character = nil
	profile.RespawnAt = nil
	profile.Folder:SetAttribute("Alive", false)
	profile.Folder:SetAttribute("RespawnAt", 0)
	if character then
		character:Destroy()
	end
	if profile.Player and profile.Player.Character then
		profile.Player.Character:Destroy()
		profile.Player.Character = nil
	end
end

return HeroService
