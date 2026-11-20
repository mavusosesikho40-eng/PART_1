--[[
	BotService
	==========
	AI fighters that fill empty slots so a match works with any number of players.

	Each bot "thinks" ten times a second:
	  1. pick the closest enemy it can actually see
	  2. decide where to go: the control point, a fighting position, or home to heal
	  3. walk there (pathfinding around walls)
	  4. shoot with deliberate inaccuracy (Config.Bots.AimErrorDegrees)
	  5. use abilities according to the hero's Bot hints in Shared/Heroes.luau
]]

local PathfindingService = game:GetService("PathfindingService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)

local GameState = require(script.Parent.GameState)
local ProfileService = require(script.Parent.ProfileService)
local CombatService = require(script.Parent.CombatService)
local WeaponService = require(script.Parent.WeaponService)
local AbilityService = require(script.Parent.AbilityService)
local MapService = require(script.Parent.MapService)

-- Roblox's default R15 animations (public assets owned by Roblox).
local ANIMATIONS = {
	Idle = "rbxassetid://507766666",
	Run = "rbxassetid://507767714",
	Jump = "rbxassetid://507765000",
	Fall = "rbxassetid://507767968",
}

local BotService = {}

local brains = {} -- [profileId] = brain
local templates = {} -- [heroId] = bot body to clone
local rng = Random.new()

--------------------------------------------------------------------------------
-- Bodies
--------------------------------------------------------------------------------

local function buildTemplate(hero)
	local description = Instance.new("HumanoidDescription")
	description.HeadColor = Color3.fromRGB(205, 208, 215)
	description.TorsoColor = hero.Color
	description.LeftArmColor = hero.Color
	description.RightArmColor = hero.Color
	description.LeftLegColor = Color3.fromRGB(45, 48, 58)
	description.RightLegColor = Color3.fromRGB(45, 48, 58)

	local ok, model = pcall(function()
		return Players:CreateHumanoidModelFromDescription(description, Enum.HumanoidRigType.R15)
	end)
	if not ok or not model then
		warn("[BotService] Could not build a bot body: " .. tostring(model))
		return nil
	end
	for _, descendant in model:GetDescendants() do
		if descendant:IsA("BaseScript") then
			descendant:Destroy() -- we animate bots from the server instead
		end
	end
	return model
end

function BotService.CreateCharacter(profile, hero)
	if not hero then
		return nil
	end
	local template = templates[hero.Id]
	if not template then
		template = buildTemplate(hero)
		templates[hero.Id] = template
	end
	if not template then
		return nil
	end
	local model = template:Clone()
	model.Name = profile.Name
	return model
end

local function setupAnimations(humanoid: Humanoid)
	local animator = humanoid:FindFirstChildOfClass("Animator")
	if not animator then
		animator = Instance.new("Animator")
		animator.Parent = humanoid
	end
	local tracks = {}
	for name, id in ANIMATIONS do
		local animation = Instance.new("Animation")
		animation.AnimationId = id
		local ok, track = pcall(function()
			return animator:LoadAnimation(animation)
		end)
		if ok then
			tracks[name] = track
		end
	end

	local current = nil
	local function play(name: string)
		local track = tracks[name]
		if not track or track == current then
			return
		end
		if current then
			current:Stop(0.15)
		end
		current = track
		track:Play(0.15)
	end

	humanoid.Running:Connect(function(speed)
		play(if speed > 1 then "Run" else "Idle")
	end)
	humanoid.Jumping:Connect(function(active)
		if active then
			play("Jump")
		end
	end)
	humanoid.FreeFalling:Connect(function(active)
		if active then
			play("Fall")
		end
	end)
	play("Idle")
end

--------------------------------------------------------------------------------
-- Choosing heroes: aim for one tank, one support, the rest damage.
--------------------------------------------------------------------------------

function BotService.ChooseHero(profile)
	local roleCounts = { Tank = 0, Damage = 0, Support = 0 }
	local taken = {}
	for _, mate in ProfileService.OnTeam(profile.Team) do
		local hero = mate ~= profile and Heroes.Get(mate.HeroId)
		if hero then
			roleCounts[hero.Role] += 1
			taken[hero.Id] = true
		end
	end

	local wantedRole = "Damage"
	if roleCounts.Tank == 0 then
		wantedRole = "Tank"
	elseif roleCounts.Support == 0 then
		wantedRole = "Support"
	end

	local candidates = {}
	for _, hero in Heroes.GetOrdered() do
		if hero.Role == wantedRole and (Config.Match.AllowDuplicateHeroes or not taken[hero.Id]) then
			table.insert(candidates, hero.Id)
		end
	end
	if #candidates == 0 then
		for _, hero in Heroes.GetOrdered() do
			if Config.Match.AllowDuplicateHeroes or not taken[hero.Id] then
				table.insert(candidates, hero.Id)
			end
		end
	end
	if #candidates == 0 then
		return Heroes.Order[1]
	end
	return candidates[rng:NextInteger(1, #candidates)]
end

--------------------------------------------------------------------------------
-- Brain helpers
--------------------------------------------------------------------------------

local function findTarget(brain)
	local character, root = brain.Character, brain.Root
	local eye = CombatUtil.GetMuzzle(character)
	local best, bestScore, bestDistance = nil, math.huge, math.huge
	for _, enemy in CombatService.GetEnemiesInRadius(character, root.Position, Config.Bots.SightRange) do
		local enemyRoot = CombatUtil.GetRoot(enemy)
		if enemyRoot and eye then
			local distance = (enemyRoot.Position - root.Position).Magnitude
			local score = distance - (if enemy == brain.Target then 15 else 0) -- stick with the current target
			if score < bestScore and CombatService.HasLineOfSight(character, eye, enemyRoot.Position) then
				best, bestScore, bestDistance = enemy, score, distance
			end
		end
	end
	return best, bestDistance
end

-- Supports look for hurt teammates to heal with their primary fire.
local function findHurtAlly(brain, range: number)
	local character, root = brain.Character, brain.Root
	local eye = CombatUtil.GetMuzzle(character)
	local best, lowest = nil, 0.8
	for _, ally in CombatService.GetAlliesInRadius(character, root.Position, range, false) do
		local humanoid = CombatUtil.GetHumanoid(ally)
		local allyRoot = CombatUtil.GetRoot(ally)
		if humanoid and allyRoot and eye then
			local fraction = humanoid.Health / humanoid.MaxHealth
			if fraction < lowest and CombatService.HasLineOfSight(character, eye, allyRoot.Position) then
				best, lowest = ally, fraction
			end
		end
	end
	return best
end

local function predictAim(brain, hero, target: Model): Vector3
	local targetRoot = CombatUtil.GetRoot(target)
	local aim = targetRoot.Position + Vector3.new(0, 0.6, 0)
	local weapon = hero.Primary
	if weapon.Kind == "Projectile" then
		local origin = CombatUtil.GetMuzzle(brain.Character) or brain.Root.Position
		local travelTime = (aim - origin).Magnitude / weapon.ProjectileSpeed
		aim += targetRoot.AssemblyLinearVelocity * Vector3.new(1, 0.3, 1) * travelTime * 0.85
	end
	return aim
end

local function hasClearWalk(from: Vector3, to: Vector3): boolean
	local flatTarget = Vector3.new(to.X, from.Y, to.Z)
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = CombatUtil.GetAllCharacters()
	return workspace:Raycast(from, flatTarget - from, params) == nil
end

local function computePath(brain, destination: Vector3)
	brain.PathComputing = true
	brain.NextPathTime = os.clock() + 2.5
	task.spawn(function()
		local path = PathfindingService:CreatePath({
			AgentRadius = 2.2,
			AgentHeight = 5.5,
			AgentCanJump = true,
			WaypointSpacing = 5,
		})
		local ok = pcall(function()
			path:ComputeAsync(brain.Root.Position, destination)
		end)
		brain.PathComputing = false
		if ok and path.Status == Enum.PathStatus.Success then
			brain.Path = path:GetWaypoints()
			brain.PathIndex = 2
			brain.PathGoal = destination
		else
			brain.Path = nil
			brain.PathGoal = nil
		end
	end)
end

local function moveTowards(brain, destination: Vector3?)
	local humanoid, root = brain.Humanoid, brain.Root
	if not destination then
		humanoid:MoveTo(root.Position)
		brain.Path = nil
		return
	end

	local flatDistance = (Vector3.new(destination.X, root.Position.Y, destination.Z) - root.Position).Magnitude
	if flatDistance < 30 and hasClearWalk(root.Position, destination) then
		brain.Path = nil
		humanoid:MoveTo(destination)
	else
		local now = os.clock()
		local goalMoved = brain.PathGoal == nil or (brain.PathGoal - destination).Magnitude > 12
		local throttled = now - (brain.LastPathRequest or 0) < 0.5 -- never more than 2 requests a second
		if not brain.PathComputing and not throttled and (goalMoved or now > brain.NextPathTime) then
			brain.LastPathRequest = now
			computePath(brain, destination)
		end
		local waypoints = brain.Path
		if waypoints then
			local waypoint = waypoints[brain.PathIndex]
			while waypoint and (Vector3.new(waypoint.Position.X, root.Position.Y, waypoint.Position.Z) - root.Position).Magnitude < 3 do
				brain.PathIndex += 1
				waypoint = waypoints[brain.PathIndex]
			end
			if waypoint then
				if waypoint.Action == Enum.PathWaypointAction.Jump then
					humanoid.Jump = true
				end
				humanoid:MoveTo(waypoint.Position)
			else
				brain.Path = nil
				humanoid:MoveTo(destination)
			end
		else
			humanoid:MoveTo(destination)
		end
	end

	-- Unstick: if we barely moved, hop and re-plan.
	local moved = (root.Position - brain.LastPosition).Magnitude
	brain.LastPosition = root.Position
	if moved < 0.3 and flatDistance > 3 then
		brain.StuckTime += Config.Bots.ThinkInterval
		if brain.StuckTime > 0.8 then
			humanoid.Jump = true
			brain.NextPathTime = 0
			brain.StuckTime = 0
		end
	else
		brain.StuckTime = 0
	end
end

local function abilityReady(brain, slot: string): boolean
	if slot == "Ultimate" then
		return ProfileService.GetUltCharge(brain.Profile) >= 100
	end
	return (brain.Character:GetAttribute("CD_" .. slot) or 0) <= workspace:GetServerTimeNow()
end

local function shouldUse(brain, usage: string?, target: Model?, distance: number): boolean
	local health = brain.Humanoid.Health / brain.Humanoid.MaxHealth
	if usage == "Engage" then
		return target ~= nil and distance > 8 and distance < 35
	elseif usage == "Attack" then
		return target ~= nil and distance < 45
	elseif usage == "AttackClose" then
		return target ~= nil and distance < 12
	elseif usage == "Defend" then
		return target ~= nil and distance < 40
	elseif usage == "Escape" then
		return target ~= nil and health < 0.45
	elseif usage == "Travel" then
		return target == nil
			and GameState.Phase == "Battle"
			and (MapService.GetPointPosition() - brain.Root.Position).Magnitude > 50
	elseif usage == "HealAllies" then
		for _, ally in CombatService.GetAlliesInRadius(brain.Character, brain.Root.Position, 20, true) do
			local humanoid = CombatUtil.GetHumanoid(ally)
			if humanoid and humanoid.Health / humanoid.MaxHealth < 0.7 then
				return true
			end
		end
		return false
	elseif usage == "TeamUlt" then
		local allies = CombatService.GetAlliesInRadius(brain.Character, brain.Root.Position, 25, false)
		return target ~= nil and (#allies > 0 or health < 0.6)
	end
	return false
end

local function makeAim(brain, usage: string?, target: Model?)
	local origin = CombatUtil.GetMuzzle(brain.Character) or brain.Root.Position
	local point
	if usage == "Travel" then
		point = MapService.GetPointPosition()
	elseif usage == "Escape" and target then
		local away = brain.Root.Position - CombatUtil.GetRoot(target).Position
		point = brain.Root.Position + CombatUtil.Flatten(away) * 30
	elseif target then
		point = CombatUtil.GetRoot(target).Position
	else
		point = origin + brain.Root.CFrame.LookVector * 30
	end
	local direction = point - origin
	direction = if direction.Magnitude > 0.1 then direction.Unit else brain.Root.CFrame.LookVector
	return { Direction = direction, Point = point }
end

local function tryAbilities(brain, hero, target: Model?, distance: number)
	local hints = hero.Bot or {}
	for _, slot in { "Ultimate", "Ability1", "Ability2" } do
		local usage = hints[slot]
		if abilityReady(brain, slot) and shouldUse(brain, usage, target, distance) then
			if AbilityService.TryUse(brain.Character, slot, makeAim(brain, usage, target)) then
				return
			end
		end
	end
end

--------------------------------------------------------------------------------
-- Thinking
--------------------------------------------------------------------------------

local function think(brain)
	local character, humanoid, root, profile = brain.Character, brain.Humanoid, brain.Root, brain.Profile
	local hero = Heroes.Get(character:GetAttribute("Hero"))
	if not hero then
		return
	end
	local now = os.clock()
	local phase = GameState.Phase
	local fighting = phase == "Battle"

	-- 1. Target
	if now >= brain.NextTargetScan then
		brain.NextTargetScan = now + 0.3
		local target, distance = findTarget(brain)
		if target ~= brain.Target then
			brain.TargetSince = now
		end
		brain.Target, brain.TargetDistance = target, distance
	end
	local target = brain.Target
	if target and not CombatUtil.IsAlive(target) then
		target = nil
		brain.Target = nil
	end
	local targetRoot = target and CombatUtil.GetRoot(target)
	local distance = if targetRoot then (targetRoot.Position - root.Position).Magnitude else math.huge

	-- 2. Retreat when badly hurt, return when healed.
	local healthFraction = humanoid.Health / humanoid.MaxHealth
	if healthFraction < Config.Bots.RetreatHealthFraction then
		brain.Retreating = true
	elseif healthFraction > 0.9 then
		brain.Retreating = false
	end

	-- 3. Where to go
	if now > brain.NextStrafeFlip then
		brain.StrafeDirection = -brain.StrafeDirection
		brain.NextStrafeFlip = now + rng:NextNumber(0.6, 1.6)
	end
	local destination = nil
	if not fighting then
		destination = nil -- wait in spawn during setup
	elseif brain.Retreating then
		brain.HomePoint = brain.HomePoint or MapService.GetSpawnCFrame(profile.Team).Position
		destination = brain.HomePoint
	else
		brain.HomePoint = nil
		local preferred = hero.Bot and hero.Bot.PreferredRange or 20
		if targetRoot and distance < preferred * 1.6 then
			local toward = CombatUtil.Flatten(targetRoot.Position - root.Position)
			local side = Vector3.new(-toward.Z, 0, toward.X) * brain.StrafeDirection
			if distance > preferred + 4 then
				destination = root.Position + toward * 8 + side * 3
			elseif distance < preferred - 4 then
				destination = root.Position - toward * 6 + side * 3
			else
				destination = root.Position + side * 6
			end
		else
			if not brain.PointSpot or now > brain.NextSpotTime then
				local angle = rng:NextNumber() * math.pi * 2
				local radius = rng:NextNumber() * 10
				brain.PointSpot = MapService.GetPointPosition() + Vector3.new(math.cos(angle) * radius, 0, math.sin(angle) * radius)
				brain.NextSpotTime = now + rng:NextNumber(3, 6)
			end
			destination = brain.PointSpot
		end
	end
	moveTowards(brain, destination)

	-- 4. Shoot (or heal a teammate if this hero's gun heals)
	local aimTarget = nil
	if fighting and hero.Primary.Heal and not (target and distance < 15) then
		aimTarget = findHurtAlly(brain, 60)
	end
	if not aimTarget and target and now - brain.TargetSince >= Config.Bots.ReactionTime then
		local weapon = hero.Primary
		local reach = if weapon.Kind == "Melee" then weapon.Range + 1.5 else math.min(weapon.Range, Config.Bots.SightRange)
		if distance <= reach then
			aimTarget = target
		end
	end
	if aimTarget and fighting then
		WeaponService.BotFire(character, predictAim(brain, hero, aimTarget))
		brain.FacePosition = CombatUtil.GetRoot(aimTarget).Position
	else
		brain.FacePosition = if targetRoot then targetRoot.Position else nil
		if not target and (character:GetAttribute("Ammo") or 0) < (character:GetAttribute("MaxAmmo") or 0) then
			WeaponService.Reload(character)
		end
	end

	-- 5. Abilities
	if fighting and now >= brain.NextAbilityCheck then
		brain.NextAbilityCheck = now + 0.4
		tryAbilities(brain, hero, target, distance)
	end
end

function BotService.OnSpawned(profile, character: Model)
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	local root = CombatUtil.GetRoot(character)
	if not humanoid or not root then
		return
	end
	pcall(function()
		root:SetNetworkOwner(nil) -- the server simulates bots
	end)
	humanoid:SetStateEnabled(Enum.HumanoidStateType.FallingDown, false)
	humanoid:SetStateEnabled(Enum.HumanoidStateType.Ragdoll, false)
	setupAnimations(humanoid)

	brains[profile.Id] = {
		Profile = profile,
		Character = character,
		Humanoid = humanoid,
		Root = root,
		Target = nil,
		TargetDistance = math.huge,
		TargetSince = 0,
		NextTargetScan = 0,
		Path = nil,
		PathIndex = 0,
		PathGoal = nil,
		PathComputing = false,
		NextPathTime = 0,
		LastPosition = root.Position,
		StuckTime = 0,
		StrafeDirection = 1,
		NextStrafeFlip = 0,
		PointSpot = nil,
		NextSpotTime = 0,
		Retreating = false,
		HomePoint = nil,
		NextAbilityCheck = 0,
		FacePosition = nil,
	}
end

function BotService.Init()
	task.spawn(function()
		while true do
			task.wait(Config.Bots.ThinkInterval)
			for id, brain in brains do
				local alive = brain.Profile.Character == brain.Character and CombatUtil.IsAlive(brain.Character)
				if not alive or ProfileService.Get(id) ~= brain.Profile then
					brains[id] = nil
				else
					local ok, err = pcall(think, brain)
					if not ok then
						warn("[BotService] " .. tostring(err))
					end
				end
			end
		end
	end)

	-- Turn bots to face whoever they are fighting (every frame, for smooth aiming).
	RunService.Heartbeat:Connect(function()
		for _, brain in brains do
			local face = brain.FacePosition
			if face and brain.Root.Parent then
				brain.Humanoid.AutoRotate = false
				local position = brain.Root.Position
				local flat = Vector3.new(face.X, position.Y, face.Z)
				if (flat - position).Magnitude > 0.5 then
					brain.Root.CFrame = CFrame.lookAt(position, flat)
				end
			else
				brain.Humanoid.AutoRotate = true
			end
		end
	end)
end

return BotService
