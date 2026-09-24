INSERT INTO sys_user (username, password_hash, display_name, is_admin, enabled)
VALUES
    ('admin', '$2a$12$gWbnQ5VembKCJh1W1mEd0eaOGa/pwyUPw3Izrt1Iozc31y.uayq0m', '系统管理员', 1, 1),
    ('viewer', '$2a$12$zk2aHgKG3xo.uiz3Wa6GK.Mcl1pOG0VV5uqjU.nitKfyA3txWRg9e', '日志查询用户', 0, 1),
    ('disabled', '$2a$12$zk2aHgKG3xo.uiz3Wa6GK.Mcl1pOG0VV5uqjU.nitKfyA3txWRg9e', '停用用户', 0, 0);
