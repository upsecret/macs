-- ============================================================
-- MACS System – Oracle Init Script
-- Target: gvenzl/oracle-free:23-slim (FREEPDB1)
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. Application Schema
-- ────────────────────────────────────────────────────────────
CREATE USER macs IDENTIFIED BY macs_password
    DEFAULT TABLESPACE users
    TEMPORARY TABLESPACE temp
    QUOTA UNLIMITED ON users;

-- Oracle 23c: CONNECT/RESOURCE roles may not include CREATE SESSION
GRANT CREATE SESSION TO macs;
GRANT CREATE TABLE TO macs;
GRANT CREATE VIEW TO macs;
GRANT CREATE SEQUENCE TO macs;
GRANT UNLIMITED TABLESPACE TO macs;

ALTER SESSION SET CURRENT_SCHEMA = macs;

-- ============================================================
-- 2. Config Server – PROPERTIES
-- ============================================================
CREATE TABLE PROPERTIES (
    APPLICATION  VARCHAR2(128)  NOT NULL,
    PROFILE      VARCHAR2(128)  NOT NULL,
    LABEL        VARCHAR2(128)  NOT NULL,
    PROP_KEY     VARCHAR2(256)  NOT NULL,
    PROP_VALUE   VARCHAR2(4000),
    CONSTRAINT PK_PROPERTIES PRIMARY KEY (APPLICATION, PROFILE, LABEL, PROP_KEY)
);

CREATE INDEX IDX_PROP_APP_PROFILE ON PROPERTIES (APPLICATION, PROFILE);

-- ============================================================
-- 3. Auth Server – Tables
-- ============================================================

-- ── APP_INFO ────────────────────────────────────────────────
CREATE TABLE APP_INFO (
    APP_ID       VARCHAR2(64)   NOT NULL,
    APP_NAME     VARCHAR2(128)  NOT NULL,
    DESCRIPTION  VARCHAR2(512),
    CREATED_AT   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_APP_INFO PRIMARY KEY (APP_ID)
);

-- ── GROUP_INFO ──────────────────────────────────────────────
CREATE TABLE GROUP_INFO (
    GROUP_ID     VARCHAR2(64)   NOT NULL,
    APP_ID       VARCHAR2(64)   NOT NULL,
    GROUP_NAME   VARCHAR2(128)  NOT NULL,
    CONSTRAINT PK_GROUP_INFO   PRIMARY KEY (GROUP_ID),
    CONSTRAINT FK_GI_APP       FOREIGN KEY (APP_ID) REFERENCES APP_INFO (APP_ID),
    CONSTRAINT UQ_GI_APP_NAME  UNIQUE (APP_ID, GROUP_NAME)
);

CREATE INDEX IDX_GI_APP_ID ON GROUP_INFO (APP_ID);

-- ── GROUP_MEMBER ────────────────────────────────────────────
CREATE TABLE GROUP_MEMBER (
    GROUP_ID         VARCHAR2(64)  NOT NULL,
    EMPLOYEE_NUMBER  VARCHAR2(32)  NOT NULL,
    CONSTRAINT PK_GROUP_MEMBER PRIMARY KEY (GROUP_ID, EMPLOYEE_NUMBER),
    CONSTRAINT FK_GM_GROUP     FOREIGN KEY (GROUP_ID) REFERENCES GROUP_INFO (GROUP_ID)
);

CREATE INDEX IDX_GM_EMPLOYEE ON GROUP_MEMBER (EMPLOYEE_NUMBER);

-- ── GROUP_RESOURCE ──────────────────────────────────────────
CREATE TABLE GROUP_RESOURCE (
    GROUP_ID       VARCHAR2(64)   NOT NULL,
    RESOURCE_NAME  VARCHAR2(256)  NOT NULL,
    CONSTRAINT PK_GROUP_RESOURCE PRIMARY KEY (GROUP_ID, RESOURCE_NAME),
    CONSTRAINT FK_GR_GROUP       FOREIGN KEY (GROUP_ID) REFERENCES GROUP_INFO (GROUP_ID)
);

-- ── USER_RESOURCE ───────────────────────────────────────────
CREATE TABLE USER_RESOURCE (
    EMPLOYEE_NUMBER  VARCHAR2(32)   NOT NULL,
    APP_ID           VARCHAR2(64)   NOT NULL,
    RESOURCE_NAME    VARCHAR2(256)  NOT NULL,
    CONSTRAINT PK_USER_RESOURCE PRIMARY KEY (EMPLOYEE_NUMBER, APP_ID, RESOURCE_NAME),
    CONSTRAINT FK_UR_APP        FOREIGN KEY (APP_ID) REFERENCES APP_INFO (APP_ID)
);

CREATE INDEX IDX_UR_EMPLOYEE ON USER_RESOURCE (EMPLOYEE_NUMBER);
CREATE INDEX IDX_UR_APP_ID   ON USER_RESOURCE (APP_ID);

-- ============================================================
-- 4. Sample Data – Config Server PROPERTIES
-- ============================================================

-- ── Shared (application) ────────────────────────────────────
INSERT INTO PROPERTIES VALUES ('application', 'default', 'main', 'management.endpoints.web.exposure.include', 'health,info,prometheus');
INSERT INTO PROPERTIES VALUES ('application', 'default', 'main', 'management.endpoint.health.show-details', 'always');
INSERT INTO PROPERTIES VALUES ('application', 'default', 'main', 'management.endpoint.health.probes.enabled', 'true');

-- ── Gateway Service – Routes ────────────────────────────────
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[0].id',            'portal-route');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[0].uri',           'http://portal:3000');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[0].predicates[0]', 'Path=/portal/**');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[0].filters[0]',    'StripPrefix=1');

INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[1].id',            'auth-route');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[1].uri',           'http://auth-server:8080');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[1].predicates[0]', 'Path=/auth/**');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.routes[1].filters[0]',    'StripPrefix=1');

-- ── Gateway Service – CORS ──────────────────────────────────
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-origins[0]', 'http://localhost:3000');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods[0]', 'GET');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods[1]', 'POST');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods[2]', 'PUT');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods[3]', 'DELETE');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-headers[0]', '*');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'spring.cloud.gateway.globalcors.cors-configurations.[/**].allow-credentials',  'true');

-- ── Gateway Service – Rate Limit ────────────────────────────
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'gateway.rate-limit.capacity',       '100');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'gateway.rate-limit.refill-tokens',  '100');
INSERT INTO PROPERTIES VALUES ('gateway-service', 'default', 'main', 'gateway.rate-limit.refill-duration', 'PT1M');

-- ── Auth Server ─────────────────────────────────────────────
INSERT INTO PROPERTIES VALUES ('auth-server', 'default', 'main', 'jwt.secret',           'macs-jwt-secret-key-for-development-only-change-in-production');
INSERT INTO PROPERTIES VALUES ('auth-server', 'default', 'main', 'jwt.expiration-ms',    '3600000');
INSERT INTO PROPERTIES VALUES ('auth-server', 'default', 'main', 'jwt.refresh-expiration-ms', '86400000');

-- ============================================================
-- 5. Sample Data – Auth Server
-- ============================================================

-- ── APP_INFO ────────────────────────────────────────────────
INSERT INTO APP_INFO (APP_ID, APP_NAME, DESCRIPTION)
VALUES ('portal', 'portal', NULL);

-- ── GROUP_INFO ──────────────────────────────────────────────
INSERT INTO GROUP_INFO (GROUP_ID, APP_ID, GROUP_NAME)
VALUES ('portal-admin', 'portal', 'admin');

INSERT INTO GROUP_INFO (GROUP_ID, APP_ID, GROUP_NAME)
VALUES ('portal-developer', 'portal', 'developer');

INSERT INTO GROUP_INFO (GROUP_ID, APP_ID, GROUP_NAME)
VALUES ('portal-operator', 'portal', 'operator');

INSERT INTO GROUP_INFO (GROUP_ID, APP_ID, GROUP_NAME)
VALUES ('portal-user', 'portal', 'user');

-- ── GROUP_MEMBER ────────────────────────────────────────────
-- 2078432: 모든 앱 admin
INSERT INTO GROUP_MEMBER VALUES ('portal-admin', '2078432');

-- 2065162: portal=operator
INSERT INTO GROUP_MEMBER VALUES ('portal-operator', '2065162');

-- ── GROUP_RESOURCE ──────────────────────────────────────────
-- admin: full access
INSERT INTO GROUP_RESOURCE VALUES ('portal-admin', '/api/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-admin', '/admin/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-admin', '/actuator/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-admin', '/portal/admin/**');

-- developer: API + docs
INSERT INTO GROUP_RESOURCE VALUES ('portal-developer', '/api/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-developer', '/swagger-ui/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-developer', '/v3/api-docs/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-developer', '/portal/**');

-- operator: monitoring
INSERT INTO GROUP_RESOURCE VALUES ('portal-operator', '/api/monitoring/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-operator', '/actuator/health');
INSERT INTO GROUP_RESOURCE VALUES ('portal-operator', '/actuator/info');
INSERT INTO GROUP_RESOURCE VALUES ('portal-operator', '/actuator/prometheus');

-- user: basic API
INSERT INTO GROUP_RESOURCE VALUES ('portal-user', '/api/v1/**');
INSERT INTO GROUP_RESOURCE VALUES ('portal-user', '/portal/**');

-- ── RMS App: validation-history ─────────────────────────────
INSERT INTO APP_INFO (APP_ID, APP_NAME, DESCRIPTION)
VALUES ('rms', 'rms', 'validation-history');

INSERT INTO GROUP_INFO VALUES ('rms-admin', 'rms', 'admin');
INSERT INTO GROUP_INFO VALUES ('rms-user', 'rms', 'user');

INSERT INTO GROUP_MEMBER VALUES ('rms-admin', '2078432');
INSERT INTO GROUP_MEMBER VALUES ('rms-admin', '2065162');

INSERT INTO GROUP_RESOURCE VALUES ('rms-admin', '/api/validation-history/**');
INSERT INTO GROUP_RESOURCE VALUES ('rms-user', '/api/validation-history/**');

-- ── EES App: token-dictionary ───────────────────────────────
INSERT INTO APP_INFO (APP_ID, APP_NAME, DESCRIPTION)
VALUES ('ees', 'ees', 'token-dictionary');

INSERT INTO GROUP_INFO VALUES ('ees-admin', 'ees', 'admin');
INSERT INTO GROUP_INFO VALUES ('ees-user', 'ees', 'user');

INSERT INTO GROUP_MEMBER VALUES ('ees-admin', '2078432');
INSERT INTO GROUP_MEMBER VALUES ('ees-admin', '2065162');

INSERT INTO GROUP_RESOURCE VALUES ('ees-admin', '/api/token-dictionary/**');
INSERT INTO GROUP_RESOURCE VALUES ('ees-user', '/api/token-dictionary/**');

-- ── USER_RESOURCE (per-user overrides) ──────────────────────
INSERT INTO USER_RESOURCE VALUES ('2078432', 'portal', '/actuator/busrefresh');

COMMIT;
