class_name DonutTransition
extends BaseTransition

const CIRCLE_POINTS = 32
var _last_progress: = 0.0

func create_circle_points(center: Vector2, radius: float) -> PackedVector2Array:
    var points = PackedVector2Array()
    for i in range(CIRCLE_POINTS + 1):
        var angle = i * 2 * PI / CIRCLE_POINTS
        var point = center + Vector2(cos(angle), sin(angle)) * radius
        points.push_back(point)
    return points

func on_transition(drawer: Control, progress: float) -> void :
    var viewport_size = drawer.get_viewport_rect().size
    var center = viewport_size / 2
    if _last_progress <= 0.5 and progress >= 0.5:
        TransitionDrawer.draw_full_rect(drawer, viewport_size)
        _last_progress = progress
        return
    _last_progress = progress
    var max_radius = max(viewport_size.x, viewport_size.y)
    var donut_points = PackedVector2Array()
    if progress <= 0.5:
        var outer_radius = max_radius * (1 - clamp(progress + 0.2, 0.0, 1.0) * 2.0)
        var inner_radius = max_radius

        var outer_points = create_circle_points(center, outer_radius)
        var inner_points = create_circle_points(center, inner_radius)


        donut_points.append_array(outer_points)
        inner_points.reverse()
        donut_points.append_array(inner_points)
    else:
        var outer_radius = max_radius
        var inner_radius = max_radius * ((progress - 0.5) * 2.0)

        var outer_points = create_circle_points(center, outer_radius)
        var inner_points = create_circle_points(center, inner_radius)

        donut_points.append_array(outer_points)
        inner_points.reverse()
        donut_points.append_array(inner_points)

    if Geometry2D.triangulate_polygon(donut_points).is_empty():
        TransitionDrawer.draw_full_rect(drawer, viewport_size)
        return
    drawer.draw_colored_polygon(donut_points, Color(0, 0, 0, 1))
