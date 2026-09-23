extends Node2D

const SAVE_PATH: = "res://contexts/automation_context/out/automation-data.json"
var IS_ACTIVE = false
var motion_event = InputEventMouseMotion.new()
var mouse_event = InputEventMouseButton.new()
var target_coin: Coin = null
var virtual_mouse_position = Vector2.ZERO:
    set(value):
        virtual_mouse_position = value
        if not target_coin: return
        motion_event.position = virtual_mouse_position
        Input.parse_input_event(motion_event)
var play_room: PlayRoom
var clicker_delay: = 0.0
var CLICKER_DELAY_MAX: = 0.7
var last_save_seconds: = 0
var seconds_passed: = 0
var interactable_parent: Node2D

var data = []
var data_points: = 0
var last_data_point = {"seconds": 0, "money": "0", "events": []}

func _ready() -> void :
    var args = OS.get_cmdline_args()
    for arg in args:
        if arg.begins_with("--automation"):
            IS_ACTIVE = true
    if not IS_ACTIVE: return
    RenderingServer.render_loop_enabled = false
    AudioServer.set_bus_volume_linear(0, -999)

    top_level = true
    z_index = 100
    _create_data_point([])
    Engine.time_scale = 100.0
    Engine.physics_ticks_per_second = 120


func _physics_process(delta: float) -> void :
    if not IS_ACTIVE: return
    var viewport = get_viewport()
    viewport.push_input(motion_event)
    if play_room and clicker_delay <= 0:
        clicker_delay = CLICKER_DELAY_MAX
        _click_coin()
    else:
        clicker_delay -= delta
    queue_redraw()

func _create_data_point(events: Array[String], time = seconds_passed):
    if time == last_data_point.seconds:

        last_data_point.events.append_array(events)
        last_data_point.money = StatsContext.money.toString().split(".")[0]
    else:
        data.push_back(last_data_point)
        last_data_point = {
            "seconds": time, 
            "money": StatsContext.money.toString().split(".")[0], 
            "events": events
        }
        data_points += 1



func trigger_auto_buy(shop_item: ShopItem):

    if not IS_ACTIVE: return
    if shop_item.can_buy():
        _create_data_point(["BOUGHT__" + shop_item.purchasable.name_key])

        if shop_item.purchasable.get_purchase_amount() >= GameValuesContext.get_value_by_full_key(shop_item.purchasable.max_purchase_amount_key):
            _create_data_point(["COMPLETED__" + shop_item.purchasable.name_key])
        shop_item.on_buy()

func register_automated_clicker(current_play_room: PlayRoom):
    if not IS_ACTIVE: return
    play_room = current_play_room

func _click_coin():
    if not IS_ACTIVE: return
    target_coin = null
    if play_room.large_coins.size() > 0:
        target_coin = play_room.large_coins.pick_random()
    elif play_room.medium_coins.size() > 0:
        target_coin = play_room.medium_coins.pick_random()
    else:
        target_coin = play_room.small_coins.pick_random()

    if not target_coin: return
    interactable_parent = target_coin.get_parent()
    var tween = TweenHelper.tween("virtual_mouse", self)
    var target_pos = get_tree().current_scene.get_viewport().get_screen_transform() * get_tree().current_scene.get_global_transform_with_canvas() * target_coin.global_position
    tween.tween_property(self, "virtual_mouse_position", target_pos, CLICKER_DELAY_MAX)
    tween.tween_callback(func():
        var _temp = target_coin.global_position
        mouse_event.position = motion_event.position
        mouse_event.button_index = MOUSE_BUTTON_LEFT
        mouse_event.double_click = false
        mouse_event.pressed = true
        Input.parse_input_event(mouse_event.duplicate())
        get_viewport().warp_mouse(mouse_event.position)
        Input.action_press("trigger_coin")



        await get_tree().physics_frame
        Input.action_release("trigger_coin")
        mouse_event.pressed = false
        Input.parse_input_event(mouse_event.duplicate())
    )

func _draw() -> void :
    if not IS_ACTIVE: return

    if not target_coin or not interactable_parent: return
    var to_canvas_space = get_tree().current_scene.get_viewport().get_screen_transform().affine_inverse().basis_xform_inv(mouse_event.position)
    var circle_pos = get_tree().current_scene.get_global_transform_with_canvas().affine_inverse().basis_xform_inv(to_canvas_space)
    draw_circle(circle_pos + interactable_parent.global_position / 2.0 - Vector2(50, 50), 5, Color.WHITE)

func _on_timer_timeout() -> void :
    if not IS_ACTIVE: return
    seconds_passed += 1
    if last_data_point.seconds != seconds_passed:
        if seconds_passed < 50:
            _create_data_point([])
        elif seconds_passed % 10 == 0:
            _create_data_point([])


func _on_save_timer_timeout() -> void :
    if not IS_ACTIVE: return

    var file = FileAccess.open(SAVE_PATH, FileAccess.WRITE_READ)
    file.store_string(JSON.stringify(data))
    var size_in_bytes: int = file.get_length()
    file.close()
    print("Current automation data size: " + str(size_in_bytes / 1000000.0) + " Mb with " + str(data_points) + " entries. Passed seconds: " + str(seconds_passed) + " (delta-seconds per real second is " + str((seconds_passed - last_save_seconds) / 3.0) + ")")

    last_save_seconds = seconds_passed
