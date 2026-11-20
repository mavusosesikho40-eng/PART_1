--[[
	WeaponService
	=============
	Primary fire for every hero: hitscan guns, projectiles and melee, plus ammo and reloading.

	Players: the client aims and reports what it hit (so shooting feels instant even with lag);
	the server checks the claim is believable (fire rate, ammo, range, target position, walls)
	before applying any damage.
	Bots: the server does the whole shot itself (BotFire).
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)
local Remotes = require(Shared.Remotes)

local ProfileService = require(script.Parent.ProfileService)
local CombatService = require(script.Parent.CombatService)
local ProjectileService = require(script.Parent.ProjectileService)
local StatusService = require(script.Parent.StatusService)
local SynergyService = require(script.Parent.SynergyService)
local DestructibleService = require(script.Parent.DestructibleService)
local FX = require(script.Parent.FX)

-- How far (studs) a target may have moved between what the shooter saw and what the server sees.
-- Higher = friendlier to laggy players, lower = harder to cheat.
local MAX_TARGET_DRIFT = 14
local RANGE_TOLERANCE = 10

local WeaponService = {}

local states = {} -- [character] = { Ammo, Reloading, Budget, LastTime, ReloadToken }
local rng = Random.new()

local function getWeapon(character: Model)
	local hero = Heroes.Get(character:GetAttribute("Hero"))
	return hero and hero.Primary, hero
end

local function publishAmmo(character: Model, state)
	character:SetAttribute("Ammo", state.Ammo)
	character:SetAttribute("Reloading", state.Reloading)
end

function WeaponService.Setup(character: Model, hero)
	local magazine = hero.Primary.MagazineSize or 0
	states[character] = { Ammo = magazine, Reloading = false, Budget = 1, LastTime = os.clock() }
	character:SetAttribute("MaxAmmo", magazine)
	publishAmmo(character, states[character])
end

function WeaponService.Clear(character: Model)
	states[character] = nil
end

function WeaponService.Refill(character: Model)
	local state = states[character]
	local weapon = getWeapon(character)
	if state and weapon and weapon.MagazineSize then
		state.Ammo = weapon.MagazineSize
		state.Reloading = false
		state.ReloadToken = nil
		publishAmmo(character, state)
	end
end

function WeaponService.Reload(character: Model)
	local state = states[character]
	local weapon = getWeapon(character)
	if not state or not weapon or not weapon.MagazineSize or state.Reloading or state.Ammo >= weapon.MagazineSize then
		return
	end
	state.Reloading = true
	publishAmmo(character, state)

	local token = {}
	state.ReloadToken = token
	task.delay(weapon.ReloadTime or 1.5, function()
		if states[character] ~= state or state.ReloadToken ~= token then
			return -- died, swapped hero or reload was cancelled
		end
		state.Ammo = weapon.MagazineSize
		state.Reloading = false
		state.ReloadToken = nil
		publishAmmo(character, state)
	end)
end

-- Fire-rate and ammo check. A small "budget" absorbs network jitter without allowing rapid fire.
local function tryConsumeShot(character: Model, weapon): boolean
	local state = states[character]
	if not state then
		return false
	end
	local now = os.clock()
	local rate = weapon.FireRate * StatusService.GetMultiplier(character, "FireRate")
	state.Budget = math.min(state.Budget + (now - state.LastTime) * rate, 1.5)
	state.LastTime = now
	if state.Budget < 0.85 then
		return false
	end

	if weapon.MagazineSize and not StatusService.Has(character, "InfiniteAmmo") then
		if state.Reloading then
			return false
		end
		if state.Ammo <= 0 then
			WeaponService.Reload(character)
			return false
		end
		state.Ammo -= 1
		publishAmmo(character, state)
		if state.Ammo <= 0 then
			WeaponService.Reload(character)
		end
	end
	state.Budget -= 1
	return true
end

-- Applies a list of hitscan hits { Part, Position }. `validate` = the hits came from a client.
local function applyHitscanHits(character: Model, weapon, origin: Vector3, hits, validate: boolean)
	local totals = {} -- one damage event per target, so shotguns show one big number
	for _, hit in hits do
		local part, position = hit.Part, hit.Position
		if part:IsDescendantOf(workspace) then
			local distance = (position - origin).Magnitude
			if distance <= weapon.Range + (if validate then RANGE_TOLERANCE else 1) then
				local target = CombatUtil.GetCharacterFromPart(part)
				if target then
					if target ~= character and CombatUtil.IsAlive(target) then
						local believable = true
						if validate then
							local targetRoot = CombatUtil.GetRoot(target)
							believable = targetRoot ~= nil
								and (targetRoot.Position - position).Magnitude <= MAX_TARGET_DRIFT
								and CombatService.HasLineOfSight(character, origin, position)
						end
						if believable then
							local headshot = part.Name == "Head"
							local multiplier = CombatUtil.Falloff(weapon, distance)
							if headshot then
								multiplier *= weapon.HeadshotMultiplier or Config.Combat.DefaultHeadshotMultiplier
							end
							local entry = totals[target]
							if not entry then
								entry = { Amount = 0, Headshot = false, Position = position }
								totals[target] = entry
							end
							entry.Amount += weapon.Damage * multiplier
							entry.Headshot = entry.Headshot or headshot
						end
					end
				elseif DestructibleService.IsDestructible(part) then
					if not validate or CombatService.HasLineOfSight(character, origin, position) then
						DestructibleService.Damage(part, weapon.Damage * CombatUtil.Falloff(weapon, distance), character)
					end
				end
			end
		end
	end
	for target, entry in totals do
		CombatService.Damage(character, target, entry.Amount, {
			Ability = weapon.Name,
			Headshot = entry.Headshot,
			Position = entry.Position,
		})
	end
end

local function fireProjectile(character: Model, hero, origin: Vector3, aimPoint: Vector3, id: string?, predictedBy: Player?)
	local weapon = hero.Primary
	local direction = aimPoint - origin
	if direction.Magnitude < 0.5 then
		local root = CombatUtil.GetRoot(character)
		direction = if root then root.CFrame.LookVector else Vector3.new(0, 0, -1)
	end
	direction = direction.Unit
	local speed = weapon.ProjectileSpeed * SynergyService.GetBonus(character, "ProjectileSpeedMult", 1)

	ProjectileService.Fire({
		Owner = character,
		Id = id,
		PredictedBy = predictedBy,
		Origin = origin,
		Velocity = direction * speed,
		Gravity = weapon.ProjectileGravity or 0,
		Radius = weapon.ProjectileRadius or 0.5,
		Lifetime = weapon.Range / speed,
		HitsAllies = (weapon.Heal or 0) > 0,
		Style = hero.Id,
		Color = weapon.TracerColor,
		Impact = if weapon.SplashRadius then "Explosion" else "Spark",
		ImpactRadius = weapon.SplashRadius or 2,
		OnHit = function(hitPart, position, normal)
			local target = hitPart and CombatUtil.GetCharacterFromPart(hitPart)
			if target and target ~= character and CombatUtil.IsAlive(target) then
				if CombatUtil.AreEnemies(character, target) then
					CombatService.Damage(character, target, weapon.Damage, { Ability = weapon.Name, Position = position })
				elseif weapon.Heal then
					CombatService.Heal(character, target, weapon.Heal, { Ability = weapon.Name })
				end
			elseif hitPart and DestructibleService.IsDestructible(hitPart) then
				DestructibleService.Damage(hitPart, weapon.Damage, character)
			end
			if weapon.SplashRadius and weapon.SplashDamage then
				local center = if normal then position + normal * 0.5 else position
				CombatService.Explode(character, center, weapon.SplashRadius, weapon.SplashDamage, { Ability = weapon.Name })
			end
		end,
	})
end

local function doMelee(character: Model, hero, direction: Vector3, predictedBy: Player?)
	local weapon = hero.Primary
	local root = CombatUtil.GetRoot(character)
	if not root then
		return
	end
	local origin = root.Position + Vector3.new(0, 1, 0)
	local halfArc = math.rad((weapon.ArcDegrees or 90) / 2)

	for _, target in CombatService.GetEnemiesInRadius(character, origin, weapon.Range + 2.5) do
		local targetRoot = CombatUtil.GetRoot(target)
		if targetRoot then
			local offset = targetRoot.Position - origin
			local inArc = offset.Magnitude < 3.5
				or math.acos(math.clamp(direction:Dot(offset.Unit), -1, 1)) <= halfArc
			if inArc and CombatService.HasLineOfSight(character, origin, targetRoot.Position) then
				CombatService.Damage(character, target, weapon.Damage, { Ability = weapon.Name, Position = targetRoot.Position })
			end
		end
	end

	-- Blades chop cover too.
	local result = workspace:Raycast(origin, direction * weapon.Range, CombatUtil.ShotParams(character, true))
	if result and DestructibleService.IsDestructible(result.Instance) then
		DestructibleService.Damage(result.Instance, weapon.Damage, character)
	end

	FX.Except(predictedBy, "Slash", {
		Origin = origin,
		Direction = direction,
		Range = weapon.Range,
		Color = weapon.TracerColor,
	})
end

local function onClientFire(player: Player, payload)
	if typeof(payload) ~= "table" then
		return
	end
	local character = player.Character
	local profile = ProfileService.GetByPlayer(player)
	if not character or not profile or profile.Character ~= character or not CombatUtil.IsAlive(character) then
		return
	end
	local weapon, hero = getWeapon(character)
	if not weapon or not tryConsumeShot(character, weapon) then
		return
	end
	local origin = CombatUtil.GetMuzzle(character)
	if not origin then
		return
	end

	if weapon.Kind == "Hitscan" then
		local pellets = weapon.Pellets or 1
		local ends, hits = {}, {}
		if typeof(payload.Ends) == "table" then
			for index = 1, math.min(#payload.Ends, pellets) do
				local point = payload.Ends[index]
				if CombatUtil.IsFiniteVector(point) then
					table.insert(ends, point)
				end
			end
		end
		if typeof(payload.Hits) == "table" then
			for index = 1, math.min(#payload.Hits, pellets) do
				local hit = payload.Hits[index]
				if
					typeof(hit) == "table"
					and typeof(hit.Part) == "Instance"
					and hit.Part:IsA("BasePart")
					and CombatUtil.IsFiniteVector(hit.Position)
				then
					table.insert(hits, { Part = hit.Part, Position = hit.Position })
				end
			end
		end
		FX.Except(player, "Tracer", { Origin = origin, Ends = ends, Color = weapon.TracerColor, Width = weapon.TracerWidth })
		applyHitscanHits(character, weapon, origin, hits, true)
	elseif weapon.Kind == "Projectile" then
		local aimPoint, shotId = payload.AimPoint, payload.ShotId
		if CombatUtil.IsFiniteVector(aimPoint) and typeof(shotId) == "number" then
			local id = tostring(player.UserId) .. "_" .. tostring(math.floor(shotId))
			fireProjectile(character, hero, origin, aimPoint, id, player)
		end
	elseif weapon.Kind == "Melee" then
		local direction = payload.Direction
		if not CombatUtil.IsFiniteVector(direction) or direction.Magnitude < 0.5 then
			direction = CombatUtil.GetRoot(character).CFrame.LookVector
		end
		doMelee(character, hero, direction.Unit, player)
	end
end

-- Bots: the server aims, rolls inaccuracy and fires. Returns true if a shot went off.
function WeaponService.BotFire(character: Model, aimPoint: Vector3): boolean
	local weapon, hero = getWeapon(character)
	if not weapon or not tryConsumeShot(character, weapon) then
		return false
	end
	local origin = CombatUtil.GetMuzzle(character)
	if not origin then
		return false
	end
	local direction = aimPoint - origin
	if direction.Magnitude < 0.1 then
		return false
	end
	direction = direction.Unit
	local aimError = Config.Bots.AimErrorDegrees

	if weapon.Kind == "Hitscan" then
		local params = CombatUtil.ShotParams(character)
		local ends, hits = {}, {}
		for _ = 1, weapon.Pellets or 1 do
			local pelletDirection = CombatUtil.SpreadDirection(direction, (weapon.SpreadDegrees or 0) + aimError, rng)
			local result = workspace:Raycast(origin, pelletDirection * weapon.Range, params)
			table.insert(ends, if result then result.Position else origin + pelletDirection * weapon.Range)
			if result then
				table.insert(hits, { Part = result.Instance, Position = result.Position })
			end
		end
		FX.All("Tracer", { Origin = origin, Ends = ends, Color = weapon.TracerColor, Width = weapon.TracerWidth })
		applyHitscanHits(character, weapon, origin, hits, false)
	elseif weapon.Kind == "Projectile" then
		local spread = CombatUtil.SpreadDirection(direction, aimError, rng)
		fireProjectile(character, hero, origin, origin + spread * 100, nil, nil)
	elseif weapon.Kind == "Melee" then
		doMelee(character, hero, direction, nil)
	end
	return true
end

function WeaponService.Init()
	Remotes.Fire.OnServerEvent:Connect(onClientFire)
	Remotes.Reload.OnServerEvent:Connect(function(player)
		local character = player.Character
		if character and CombatUtil.IsAlive(character) then
			WeaponService.Reload(character)
		end
	end)
end

return WeaponService
