SELECT setval('role_id_seq', (SELECT MAX(id) FROM role));