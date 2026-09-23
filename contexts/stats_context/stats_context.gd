extends Node

enum DICE_COMBINATION{
    YAHTZEE, 
    FOUR_KIND, 
    THREE_KIND, 
    FULL_HOUSE, 
    SMALL_STRAIGHT, 
    LARGE_STRAIGHT, 
    TWO_PAIR, 
    ONE_PAIR, 
    CHANCE
}

var money: = Big.new(0):
    set(value):
        if value.isGreaterThan(money):
            total_money = Big.add(total_money, Big.subtract(value, money))
        money = value
        money_changed.emit(money)

        if money.isGreaterThan(highest_money):
            highest_money = money

var highest_money: = Big.new(0)
var highest_value_gained: = Big.new(0)
var total_money: = Big.new(0)

var prestige_points: = Big.new(0):
    set(value):
        prestige_points = value
        prestige_points_changed.emit(prestige_points)

@export var small_coins: = 1:
    set(value):
        small_coins = value
        small_coins_changed.emit(small_coins)

@export var medium_coins: = 0:
    set(value):
        medium_coins = value
        medium_coins_changed.emit(medium_coins)

@export var large_coins: = 0:
    set(value):
        large_coins = value
        large_coins_changed.emit(large_coins)

@export var die: = 0:
    set(value):
        die = value
        die_changed.emit(die)

@export var helpers: = 0:
    set(value):
        helpers = value
        helpers_changed.emit(helpers)

@export var coins_reflip_chance: = 0.0
@export var small_coin_flip_speed_multiplier: = 1.0
@export var medium_coin_flip_speed_multiplier: = 1.0
@export var large_coin_flip_speed_multiplier: = 1.0
@export var small_coin_additional_coin_value: = 0
@export var medium_coin_additional_coin_value: = 0
@export var large_coin_additional_coin_value: = 0
@export var small_additional_coin_multiplier: = 1.0
@export var medium_additional_coin_multiplier: = 1.0
@export var large_additional_coin_multiplier: = 1.0
@export var trigger_small_coins_on_mouse_over: = false
@export var large_coins_trigger_coins_on_land: = false
@export var helper_can_trigger_medium_coins: = false
@export var helper_can_trigger_large_coins: = false
@export var helper_can_trigger_die: = false
@export var coins_respect_mouse_direction: = false
@export var helper_efficiency_multiplier: = 1.0

var money_earned_by_interactable: = {}

var dice_combinations: Array[int] = []

func _ready() -> void :
    _load_from_save()

func _load_from_save():
    if not SaveContext.save_data.purchases: return
    money = SaveContext.save_data.money
    highest_money = SaveContext.save_data.highest_money
    highest_value_gained = SaveContext.save_data.highest_value_gained
    total_money = SaveContext.save_data.total_money
    prestige_points = SaveContext.save_data.prestige_points

func add_dice_to_combination(value: int):
    if dice_combinations.size() + 1 > 5:
        dice_combinations.clear()
    dice_combinations.push_front(value)
    dice_combinations_changed.emit(dice_combinations)
    if dice_combinations.size() == 5:
        dice_combination_finished.emit(dice_combinations, get_kind_of_dice_combination(dice_combinations.duplicate()))

func get_kind_of_dice_combination(combination: Array[int]) -> DICE_COMBINATION:
    combination.sort()
    var counts = {}
    for number in combination:
        counts[number] = counts.get(number, 0) + 1
    var count_values = counts.values()
    count_values.sort()
    if 5 in count_values:
        return DICE_COMBINATION.YAHTZEE
    if 4 in count_values:
        return DICE_COMBINATION.FOUR_KIND
    if count_values == [2, 3]:
        return DICE_COMBINATION.FULL_HOUSE
    var unique_numbers = []
    for number in combination:
        if not number in unique_numbers:
            unique_numbers.append(number)
    if unique_numbers == [1, 2, 3, 4] or unique_numbers == [2, 3, 4, 5] or unique_numbers == [3, 4, 5, 6]:
        return DICE_COMBINATION.SMALL_STRAIGHT
    if combination == [1, 2, 3, 4, 5] or combination == [2, 3, 4, 5, 6]:
        return DICE_COMBINATION.LARGE_STRAIGHT
    if 3 in count_values:
        return DICE_COMBINATION.THREE_KIND
    if count_values == [1, 2, 2]:
        return DICE_COMBINATION.TWO_PAIR
    if count_values.count(2) == 1 and count_values.count(1) == 3:
        return DICE_COMBINATION.ONE_PAIR
    return DICE_COMBINATION.CHANCE

var purchases: Dictionary[String, int] = {}
var one_time_purchases: Array[StringName] = []

signal small_coins_changed(current_amount: int)
signal medium_coins_changed(current_amount: int)
signal large_coins_changed(current_amount: int)
signal money_changed(current_amount: Big)
signal die_changed(current_amount: int)
signal helpers_changed(current_amount: int)
signal dice_combinations_changed(array: Array[int])
signal dice_combination_finished(array: Array[int], kind: DICE_COMBINATION)
signal prestige_points_changed(current_amount: Big)
