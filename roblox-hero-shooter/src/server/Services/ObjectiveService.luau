--[[
	ObjectiveService
	================
	The control point ("king of the hill"):
	  * The point is locked for a few seconds after the doors open.
	  * Stand on it alone to capture it. More teammates capture faster.
	  * While your team owns it, your score climbs. First to 100% wins the round.
	  * If both teams are on the point it is contested: nobody captures.
	  * Overtime: you cannot win while an enemy is standing on the point.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local CombatUtil = require(Shared.CombatUtil)

local GameState = require(script.Parent.GameState)
local ProfileService = require(script.Parent.ProfileService)
local MapService = require(script.Parent.MapService)
local FX = require(script.Parent.FX)

local ObjectiveService = {}

local TEAM_A, TEAM_B = Config.Teams[1].Name, Config.Teams[2].Name
local NEUTRAL_COLOR = Color3.fromRGB(235, 235, 240)

local state = nil

local function round1(value: number): number
	return math.floor(value * 10 + 0.5) / 10
end

local function countOnPoint()
	local counts = { [TEAM_A] = 0, [TEAM_B] = 0 }
	local point = MapService.GetControlPoint()
	if not point then
		return counts
	end
	local radius = point:GetAttribute("Radius") or 18
	local height = point:GetAttribute("Height") or 10
	for _, profile in ProfileService.All() do
		local character = profile.Character
		if character and profile.Team and counts[profile.Team] and CombatUtil.IsAlive(character) then
			local root = CombatUtil.GetRoot(character)
			if root then
				local offset = root.Position - point.Position
				if Vector2.new(offset.X, offset.Z).Magnitude <= radius and math.abs(offset.Y) <= height then
					counts[profile.Team] += 1
				end
			end
		end
	end
	return counts
end

local function paintPoint()
	local map = MapService.GetMap()
	local visual = map and map:FindFirstChild("PointVisual")
	if not visual then
		return
	end
	local color = if state.Owner then Config.GetTeamColor(state.Owner) else NEUTRAL_COLOR
	for _, part in visual:GetDescendants() do
		if part:IsA("BasePart") then
			part.Color = color
		end
	end
end

local function publish(counts)
	GameState.Set("PointOwner", state.Owner or "")
	GameState.Set("CaptureTeam", state.CaptureTeam or "")
	GameState.Set("CaptureProgress", round1(state.Progress))
	GameState.Set("PointLocked", not state.Unlocked)
	GameState.Set("PointUnlockAt", state.UnlockAt)
	GameState.Set("Contested", state.Contested)
	GameState.Set("Overtime", state.Overtime)
	GameState.Set("Score_" .. TEAM_A, round1(state.Scores[TEAM_A]))
	GameState.Set("Score_" .. TEAM_B, round1(state.Scores[TEAM_B]))
	GameState.Set("OnPoint_" .. TEAM_A, if counts then counts[TEAM_A] else 0)
	GameState.Set("OnPoint_" .. TEAM_B, if counts then counts[TEAM_B] else 0)
end

function ObjectiveService.Reset()
	state = {
		Owner = nil,
		CaptureTeam = nil,
		Progress = 0,
		Scores = { [TEAM_A] = 0, [TEAM_B] = 0 },
		Unlocked = false,
		UnlockAt = 0,
		Contested = false,
		Overtime = false,
		Winner = nil,
	}
	paintPoint()
	publish(nil)
end

-- Starts the round's objective; the point unlocks `delay` seconds from now.
function ObjectiveService.Start(delay: number)
	state.UnlockAt = workspace:GetServerTimeNow() + delay
	publish(nil)
end

-- Call regularly during "Battle". Returns the winning team once someone reaches 100%.
function ObjectiveService.Update(dt: number)
	if state.Winner then
		return state.Winner
	end
	if not state.Unlocked then
		if workspace:GetServerTimeNow() >= state.UnlockAt then
			state.Unlocked = true
			FX.Notify("Announce", { Title = "POINT UNLOCKED", Subtitle = "Capture the objective!", Duration = 2.5 })
		end
		publish(nil)
		return nil
	end

	local counts = countOnPoint()
	local present = {}
	for _, team in { TEAM_A, TEAM_B } do
		if counts[team] > 0 then
			table.insert(present, team)
		end
	end
	state.Contested = #present > 1

	local objective = Config.Objective
	local captureRate = 100 / objective.CaptureTime
	if #present == 1 then
		local team = present[1]
		if team ~= state.Owner then
			if state.CaptureTeam and state.CaptureTeam ~= team and state.Progress > 0 then
				-- Undo the other team's partial capture first.
				state.Progress = math.max(0, state.Progress - captureRate * dt)
				if state.Progress <= 0 then
					state.CaptureTeam = team
				end
			else
				state.CaptureTeam = team
				local helpers = math.min(counts[team], objective.MaxCapturers) - 1
				local speed = 1 + helpers * objective.CaptureSpeedPerExtraPlayer
				state.Progress = math.min(100, state.Progress + captureRate * speed * dt)
				if state.Progress >= 100 then
					state.Owner = team
					state.CaptureTeam = nil
					state.Progress = 0
					paintPoint()
					FX.Notify("PointCaptured", { Team = team })
				end
			end
		else
			-- Owners standing on their point wipe out the enemy's partial capture.
			state.Progress = math.max(0, state.Progress - objective.DecayPerSecond * 2 * dt)
			if state.Progress <= 0 then
				state.CaptureTeam = nil
			end
		end
	elseif #present == 0 then
		state.Progress = math.max(0, state.Progress - objective.DecayPerSecond * dt)
		if state.Progress <= 0 then
			state.CaptureTeam = nil
		end
	end

	state.Overtime = false
	if state.Owner then
		local enemy = Config.GetOtherTeam(state.Owner)
		local score = state.Scores[state.Owner] + objective.ScorePerSecond * dt
		if score >= 100 then
			if enemy and counts[enemy] > 0 then
				state.Scores[state.Owner] = 99.9
				state.Overtime = true
			else
				state.Scores[state.Owner] = 100
				state.Winner = state.Owner
			end
		else
			state.Scores[state.Owner] = score
		end
	end

	publish(counts)
	return state.Winner
end

ObjectiveService.Reset()

return ObjectiveService
