-- Generic
truncate jobb;

INSERT INTO jobb (id, data, klartidpunkt) VALUES (1, '1', null);
INSERT INTO jobb (id, data, klartidpunkt) VALUES (2, '2', null);
INSERT INTO jobb (id, data, klartidpunkt) VALUES (3, '3', null);
INSERT INTO jobb (id, data, klartidpunkt) VALUES (4, '4', null);
INSERT INTO jobb (id, data, klartidpunkt) VALUES (5, '5', null);

-- GDL
truncate forandringsarende;

INSERT INTO forandringsarende(dataleveransidentitet, request_xml, mottagentidpunkt, http_status)
VALUES('fc7cc368-5979-460c-ad20-1dd6576cac66','XML 1','2025-12-19T01:41:12Z','202');
INSERT INTO forandringsarende(dataleveransidentitet, request_xml, mottagentidpunkt, http_status)
VALUES('1098d639-32b1-4dde-816f-2fcfdfa78ce4','XML 2','2025-12-20T23:30:58Z','202');
INSERT INTO forandringsarende(dataleveransidentitet, request_xml, mottagentidpunkt, http_status)
VALUES('cb721f42-f0fe-4dc2-bd86-bdde032e88c7','XML 3','2025-12-21T12:28:09Z','202');

-- Surval
truncate surval_jobb;

INSERT INTO surval_jobb(id,mottagentidpunkt,data,status) VALUES('e6bf834c-23db-44ca-a9f7-c200d7d11384','2025-12-19T01:41:12Z','data 1','Queued');
INSERT INTO surval_jobb(id,mottagentidpunkt,data,status) VALUES('6e6edee9-35ec-4c10-82e5-c5ec0484a396','2025-12-20T23:30:58Z','data 2','Queued');
INSERT INTO surval_jobb(id,mottagentidpunkt,data,status) VALUES('517dd0ec-1c3d-4376-be61-6c5df62b214f','2025-12-21T12:28:09Z','data 3','Queued');
