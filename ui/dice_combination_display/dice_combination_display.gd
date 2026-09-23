extends PanelContainer

@export var dice_faces: Array[Texture2D]

@onready var floating_text_spawn: = $FloatingTextSpawn
@onready var dice_texture_nodes: Array[TextureRect] = [
    % FirstDice, 
    % SecondDice, 
    % ThirdDice, 
    % FourthDice, 
    % FifthDice
    ]

var allow_reset: = true

func _ready() -> void :
    var handle_die_change_callable = Callable((func(amount):

        visible = amount > 0
        handle_combination_change(StatsContext.dice_combinations)
    ))
    StatsContext.die_changed.connect(handle_die_change_callable)
    StatsContext.dice_combinations_changed.connect(handle_combination_change)
    StatsContext.dice_combination_finished.connect(handle_finished_combination)
    tree_exited.connect(func():
        StatsContext.die_changed.disconnect(handle_die_change_callable)
        StatsContext.dice_combinations_changed.disconnect(handle_combination_change)
        StatsContext.dice_combination_finished.disconnect(handle_finished_combination)
    , CONNECT_ONE_SHOT)


func handle_finished_combination(_combination: Array[int], kind: StatsContext.DICE_COMBINATION):
    allow_reset = false
    var index = 0
    get_tree().create_timer(1).timeout.connect(func(): allow_reset = true;handle_combination_change(StatsContext.dice_combinations))
    for node in dice_texture_nodes:
        var tween = TweenHelper.tween("offset", node)
        tween.tween_callback(func(): node.position.y = 10).set_delay(index * 0.1)
        tween.tween_property(node, "position", Vector2.ZERO, 0.5).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
        index += 1


    FloatingText.spawn_float_text(floating_text_spawn.global_position, get_combination_name(kind) + "!", 1.0)

func get_combination_name(kind: StatsContext.DICE_COMBINATION):
    match kind:
        StatsContext.DICE_COMBINATION.ONE_PAIR: return tr("DICE_COMBINATION_ONE_PAIR")
        StatsContext.DICE_COMBINATION.TWO_PAIR: return tr("DICE_COMBINATION_TWO_PAIRS")
        StatsContext.DICE_COMBINATION.THREE_KIND: return tr("DICE_COMBINATION_THREE_OF_A_KIND")
        StatsContext.DICE_COMBINATION.FOUR_KIND: return tr("DICE_COMBINATION_FOUR_OF_A_KIND")
        StatsContext.DICE_COMBINATION.FULL_HOUSE: return tr("DICE_COMBINATION_FULL_HOUSE")
        StatsContext.DICE_COMBINATION.YAHTZEE: return tr("DICE_COMBINATION_YAHTZEE")
        StatsContext.DICE_COMBINATION.SMALL_STRAIGHT: return tr("DICE_COMBINATION_SMALL_STRAIGHT")
        StatsContext.DICE_COMBINATION.LARGE_STRAIGHT: return tr("DICE_COMBINATION_LARGE_STRAIGHT")
        StatsContext.DICE_COMBINATION.CHANCE: return tr("DICE_COMBINATION_CHANCE")

func handle_combination_change(combination: Array[int]):
    if not allow_reset: return
    var index = 0
    for node in dice_texture_nodes:
        if index < combination.size():
            var dice_value = combination[index]
            dice_texture_nodes[index].self_modulate = Color.WHITE
            dice_texture_nodes[index].texture = dice_faces[dice_value - 1]
            if index == 0 and combination.size() != 5:
                var tween = TweenHelper.tween("offset", node)
                tween.tween_callback(func(): node.position.y = 10)
                tween.tween_property(node, "position", Vector2.ZERO, 0.5).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT)
        else:
            dice_texture_nodes[index].self_modulate = Color.TRANSPARENT
        index += 1
