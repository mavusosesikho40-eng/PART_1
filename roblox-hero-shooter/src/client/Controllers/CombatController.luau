--[[
	CombatController
	================
	Turns your input into shots and abilities.

	Shooting works like most online shooters: we aim from the camera through the crosshair, fire
	from the hero's weapon toward that point, draw the shot instantly, and tell the server what we
	hit. The server double-checks everything before any damage happens (see WeaponService).
]]

local ContextActionService = game:GetService("ContextActionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local CollectionService = game:GetService("CollectionService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local Remotes = require(Shared.Remotes)
local CombatUtil = require(Shared.CombatUtil)
local MotionUtil = require(Shared.MotionUtil)

local ClientState = require(script.Parent.ClientState)
local FXController = require(script.Parent.FXController)
local CameraController = require(script.Parent.CameraController)

local player = Players.LocalPlayer
local rng = Random.new()

local CombatController = {}

local triggerHeld = false
local nextFireTime = 0
local shotCounter = 0
local lastReloadRequest = 0

local function getArmedCharacter()
	local character = ClientState.GetLivingCharacter()
	local hero = character and Heroes.Get(character:GetAttribute("Hero"))
	if not hero then
		return nil, nil
	end
	return character, hero
end

-- The world point under the crosshair. Starts the ray level with our hero so walls behind
-- the hero (between it and the camera) are ignored.
local function getAimPoint(character: Model, range: number): Vector3
	local camera = workspace.CurrentCamera
	local look = camera.CFrame.LookVector
	local origin = camera.CFrame.Position
	local root = CombatUtil.GetRoot(character)
	if root then
		origin += look * math.max(0, (root.Position - origin):Dot(look))
	end
	local result = workspace:Raycast(origin, look * range, CombatUtil.ShotParams(character))
	return if result then result.Position else origin + look * range
end

local function requestReload()
	if os.clock() - lastReloadRequest > 0.5 then
		lastReloadRequest = os.clock()
		Remotes.Reload:FireServer()
	end
end

local function fireOnce(character: Model, hero)
	local weapon = hero.Primary
	local origin = CombatUtil.GetMuzzle(character)
	if not origin then
		return
	end
	local aimPoint = getAimPoint(character, (weapon.Range or 100) + 40)
	local direction = aimPoint - origin
	direction = if direction.Magnitude > 0.5 then direction.Unit else workspace.CurrentCamera.CFrame.LookVector

	if weapon.Kind == "Hitscan" then
		local params = CombatUtil.ShotParams(character)
		local ends, hits = {}, {}
		for _ = 1, weapon.Pellets or 1 do
			local pelletDirection = CombatUtil.SpreadDirection(direction, weapon.SpreadDegrees or 0, rng)
			local result = workspace:Raycast(origin, pelletDirection * weapon.Range, params)
			table.insert(ends, if result then result.Position else origin + pelletDirection * weapon.Range)
			if result then
				local instance = result.Instance
				if CombatUtil.GetCharacterFromPart(instance) or CollectionService:HasTag(instance, "Destructible") then
					table.insert(hits, { Part = instance, Position = result.Position })
				end
			end
		end
		FXController.Tracers(origin, ends, weapon.TracerColor, weapon.TracerWidth)
		Remotes.Fire:FireServer({ Ends = ends, Hits = hits })
		CameraController.Kick(if (weapon.Pellets or 1) > 1 then 0.03 else 0.008)
	elseif weapon.Kind == "Projectile" then
		shotCounter += 1
		FXController.SpawnProjectile({
			Id = tostring(player.UserId) .. "_" .. tostring(shotCounter),
			Origin = origin,
			Velocity = direction * weapon.ProjectileSpeed,
			Gravity = weapon.ProjectileGravity or 0,
			Lifetime = weapon.Range / weapon.ProjectileSpeed,
			Radius = weapon.ProjectileRadius,
			Color = weapon.TracerColor,
		})
		Remotes.Fire:FireServer({ AimPoint = aimPoint, ShotId = shotCounter })
		CameraController.Kick(0.012)
	elseif weapon.Kind == "Melee" then
		local look = workspace.CurrentCamera.CFrame.LookVector
		local root = CombatUtil.GetRoot(character)
		if root then
			FXController.Slash(root.Position + Vector3.new(0, 1, 0), look, weapon.Range, weapon.TracerColor)
		end
		Remotes.Fire:FireServer({ Direction = look })
	end
end

-- Runs every frame: keeps firing while the trigger is held, at the weapon's fire rate.
local function step()
	if not triggerHeld or ClientState.IsMenuOpen() then
		return
	end
	local character, hero = getArmedCharacter()
	if not character then
		return
	end
	local now = os.clock()
	if now < nextFireTime or character:GetAttribute("Reloading") then
		return
	end
	local weapon = hero.Primary
	if weapon.MagazineSize and not character:GetAttribute("InfiniteAmmo") and (character:GetAttribute("Ammo") or 0) <= 0 then
		requestReload()
		return
	end
	local fireRate = weapon.FireRate * (character:GetAttribute("FireRateMult") or 1)
	nextFireTime = now + 1 / fireRate
	fireOnce(character, hero)
	if not weapon.Automatic then
		triggerHeld = false
	end
end

local function useAbility(slot: string)
	if ClientState.IsMenuOpen() then
		return
	end
	local character, hero = getArmedCharacter()
	if not character or not hero.Abilities[slot] then
		return
	end
	if slot == "Ultimate" then
		local profile = ClientState.GetProfile()
		if not profile or (profile:GetAttribute("UltCharge") or 0) < 100 then
			ClientState.AbilityDenied:Fire(slot)
			return
		end
	elseif (character:GetAttribute("CD_" .. slot) or 0) > workspace:GetServerTimeNow() + 0.05 then
		ClientState.AbilityDenied:Fire(slot)
		return
	end

	local origin = CombatUtil.GetMuzzle(character) or workspace.CurrentCamera.CFrame.Position
	local point = getAimPoint(character, 400)
	local direction = point - origin
	direction = if direction.Magnitude > 0.5 then direction.Unit else workspace.CurrentCamera.CFrame.LookVector
	Remotes.UseAbility:FireServer(slot, { Direction = direction, Point = point })
end

local function bind(name: string, callback, inputs, title: string)
	ContextActionService:BindActionAtPriority(name, callback, true, Enum.ContextActionPriority.High.Value, table.unpack(inputs))
	ContextActionService:SetTitle(name, title)
end

local function onCharacterAdded(character: Model)
	triggerHeld = false
	nextFireTime = 0
	local humanoid = character:WaitForChild("Humanoid", 10)
	if humanoid and humanoid:IsA("Humanoid") then
		-- Big knockbacks shouldn't make our hero trip over.
		humanoid:SetStateEnabled(Enum.HumanoidStateType.FallingDown, false)
		humanoid:SetStateEnabled(Enum.HumanoidStateType.Ragdoll, false)
	end
end

function CombatController.Init()
	local controls = Config.Controls

	bind("HS_Fire", function(_, state)
		if state == Enum.UserInputState.Begin then
			triggerHeld = not ClientState.IsMenuOpen()
		elseif state == Enum.UserInputState.End or state == Enum.UserInputState.Cancel then
			triggerHeld = false
		end
		return Enum.ContextActionResult.Pass
	end, controls.Fire, "Fire")

	for _, slot in Heroes.AbilitySlots do
		bind("HS_" .. slot, function(_, state)
			if state == Enum.UserInputState.Begin then
				useAbility(slot)
			end
			return Enum.ContextActionResult.Pass
		end, controls[slot], Config.KeyLabels[slot])
	end

	bind("HS_Reload", function(_, state)
		if state == Enum.UserInputState.Begin and not ClientState.IsMenuOpen() then
			requestReload()
		end
		return Enum.ContextActionResult.Pass
	end, controls.Reload, "R")

	RunService.RenderStepped:Connect(step)

	-- The server asks us to move our own character for dashes and knockback.
	Remotes.Motion.OnClientEvent:Connect(function(kind, velocity, duration)
		local character = player.Character
		if not character or not CombatUtil.IsFiniteVector(velocity) then
			return
		end
		if kind == "Dash" then
			MotionUtil.Dash(character, velocity, if typeof(duration) == "number" then math.clamp(duration, 0, 2) else 0.2)
		elseif kind == "Impulse" then
			MotionUtil.Impulse(character, velocity)
		end
	end)

	player.CharacterAdded:Connect(onCharacterAdded)
	if player.Character then
		task.spawn(onCharacterAdded, player.Character)
	end
end

return CombatController
