--[[
	SynergyService
	==============
	Works out which team-ups (Shared/Synergies.luau) are active for each fighter and answers
	"what bonus does this fighter have for stat X?". Recalculated whenever a team or hero changes.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Synergies = require(Shared.Synergies)
local ProfileService = require(script.Parent.ProfileService)

local SynergyService = {}

local bonusesByProfile = {} -- [profileId] = { [stat] = value }

local function teamHasAll(teamHeroes, required): boolean
	for _, heroId in required do
		if not teamHeroes[heroId] then
			return false
		end
	end
	return true
end

function SynergyService.Recompute()
	local heroesByTeam = {}
	for _, profile in ProfileService.All() do
		if profile.Team and profile.HeroId then
			heroesByTeam[profile.Team] = heroesByTeam[profile.Team] or {}
			heroesByTeam[profile.Team][profile.HeroId] = true
		end
	end

	table.clear(bonusesByProfile)
	for _, profile in ProfileService.All() do
		local bonuses, names = {}, {}
		local teamHeroes = profile.Team and heroesByTeam[profile.Team]
		if teamHeroes and profile.HeroId then
			for _, synergy in Synergies.List do
				local mine = synergy.Bonuses[profile.HeroId]
				if mine and teamHasAll(teamHeroes, synergy.Heroes) then
					table.insert(names, synergy.Name)
					for stat, value in mine do
						if string.sub(stat, -4) == "Mult" then
							bonuses[stat] = (bonuses[stat] or 1) * value
						else
							bonuses[stat] = (bonuses[stat] or 0) + value
						end
					end
				end
			end
		end
		bonusesByProfile[profile.Id] = bonuses
		profile.Folder:SetAttribute("Synergies", table.concat(names, ", "))
	end
end

-- `who` can be a character Model or a profile.
function SynergyService.GetBonus(who, stat: string, default: number): number
	local profile = if typeof(who) == "Instance" then ProfileService.GetByCharacter(who) else who
	local bonuses = profile and bonusesByProfile[profile.Id]
	local value = bonuses and bonuses[stat]
	return if value ~= nil then value else default
end

function SynergyService.Init()
	ProfileService.Changed:Connect(SynergyService.Recompute)
	ProfileService.Removed:Connect(SynergyService.Recompute)
end

return SynergyService
