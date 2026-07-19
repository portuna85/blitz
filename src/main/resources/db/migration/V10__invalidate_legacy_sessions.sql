-- SessionUser now carries the immutable database user ID used for ownership
-- checks. Previously serialized sessions cannot provide that identity safely,
-- so force a one-time login refresh during this upgrade.
DELETE FROM SPRING_SESSION;
