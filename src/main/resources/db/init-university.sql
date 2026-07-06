CREATE TABLE IF NOT EXISTS tb_departments (
    dept_code VARCHAR(20) PRIMARY KEY,
    dept_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_employees (
    emp_code VARCHAR(20) PRIMARY KEY,
    emp_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_professors (
    prof_code VARCHAR(20) PRIMARY KEY,
    prof_name VARCHAR(100) NOT NULL,
    dept_code VARCHAR(20) NOT NULL REFERENCES tb_departments(dept_code)
);

CREATE TABLE IF NOT EXISTS tb_dept_heads (
    emp_code VARCHAR(20) PRIMARY KEY REFERENCES tb_employees(emp_code),
    dept_code VARCHAR(20) NOT NULL REFERENCES tb_departments(dept_code)
);

DELETE FROM tb_dept_heads;
DELETE FROM tb_professors;
DELETE FROM tb_employees;
DELETE FROM tb_departments;

-- Departments
INSERT INTO tb_departments (dept_code, dept_name) VALUES ('CS', 'Computer Science');
INSERT INTO tb_departments (dept_code, dept_name) VALUES ('MATH', 'Mathematics');
INSERT INTO tb_departments (dept_code, dept_name) VALUES ('PHY', 'Physics');

-- Employees (administrative staff, not professors)
INSERT INTO tb_employees (emp_code, emp_name) VALUES ('E001', 'Alice Admin');
INSERT INTO tb_employees (emp_code, emp_name) VALUES ('E002', 'Bob Secretary');

-- Professors (are also Employees via subclass reasoning)
INSERT INTO tb_professors (prof_code, prof_name, dept_code) VALUES ('P001', 'Carol Professor', 'CS');
INSERT INTO tb_professors (prof_code, prof_name, dept_code) VALUES ('P002', 'Dave Professor', 'MATH');
INSERT INTO tb_professors (prof_code, prof_name, dept_code) VALUES ('P003', 'Eve Professor', 'PHY');

-- Department heads (headOf is subProperty of worksFor)
INSERT INTO tb_dept_heads (emp_code, dept_code) VALUES ('E001', 'CS');
INSERT INTO tb_dept_heads (emp_code, dept_code) VALUES ('E002', 'MATH');
