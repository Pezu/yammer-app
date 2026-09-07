-- =============================================
-- Order point assignment  (which users work which table/bar)
-- A single-user point (allow_multiple_users = false) holds at most one row —
-- enforced in the service; multi-user points hold any number.
-- =============================================
CREATE TABLE order_point_assignment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (order_point_id, user_id)
);
CREATE INDEX idx_op_assignment_user_id ON order_point_assignment(user_id);
CREATE INDEX idx_op_assignment_order_point_id ON order_point_assignment(order_point_id);
