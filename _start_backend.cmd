@echo off
cd /d "%~dp0flowers\backend"
mvn spring-boot:run 1>"%~dp0backend-start.log" 2>"%~dp0backend-start.err.log"

