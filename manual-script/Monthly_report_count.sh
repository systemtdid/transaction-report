#!/bin/bash

# Set MySQL credentials
MYSQL_USER="root"
MYSQL_PASSWORD="PCC99th?"
MYSQL_DATABASE="fmsv_db"

# Get the previous month (March 2026 if today is 1st March 2026)
START_DATE=$(date --date="1 month ago" +%Y-%m-01)" 00:00:00"
END_DATE=$(date --date="1 month ago" +%Y-%m-31)" 23:59:59"

# Output file path for summary
SUMMARY_OUTPUT_FILE="/tmp/Count_Transaction_Summary_${START_DATE:5:2}_${START_DATE:0:4}.txt"

# Output file path for File Count Transaction per day for GSB
FILE_OUTPUT_FILE="/tmp/Count_GSB_Transaction_${START_DATE:5:2}_${START_DATE:0:4}.txt"

# Query 1 - Count transactions for KTB
KTB_COUNT=$(mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE -e "
SELECT COUNT(tranID)
FROM transactions
WHERE orgID = '0107537000882'
AND tranStatus = 'SUCCESS'
AND processedTime BETWEEN '$START_DATE'
AND '$END_DATE';
" -s -N)

# Query 2 - Count transactions for TMC
TMC_COUNT=$(mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE -e "
SELECT COUNT(tranID)
FROM transactions
WHERE orgID = '0994000953534'
AND tranStatus = 'SUCCESS'
AND processedTime BETWEEN '$START_DATE'
AND '$END_DATE';
" -s -N)

# Query 3 - Count transactions for DHIP
DHIP_COUNT=$(mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE -e "
SELECT COUNT(tranID)
FROM transactions
WHERE orgID = '0107556000051'
AND tranStatus = 'SUCCESS'
AND processedTime BETWEEN '$START_DATE'
AND '$END_DATE';
" -s -N)

# Query 4 - Count transactions for TIPxGSB
TIPxGSB_COUNT=$(mysql -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE -e "
SELECT COUNT(tranID)
FROM transactions
WHERE gatewayID = 'TIPxGSB01'
AND orgID = '0994000164891'
AND tranStatus = 'SUCCESS'
AND processedTime BETWEEN '$START_DATE'
AND '$END_DATE';
" -s -N)


# Create summary output and append to summary file (No print to terminal)
echo "KTB and TMC Summary Transaction" > "$SUMMARY_OUTPUT_FILE"
echo "KTB = $KTB_COUNT" >> "$SUMMARY_OUTPUT_FILE"
echo "TMC = $TMC_COUNT" >> "$SUMMARY_OUTPUT_FILE"
echo "======================================================" >> "$SUMMARY_OUTPUT_FILE"
echo "DHIP and TIPxGSB Summary Transaction" >> "$SUMMARY_OUTPUT_FILE"
echo "DHIP = $DHIP_COUNT" >> "$SUMMARY_OUTPUT_FILE"
echo "TIPxGSB = $TIPxGSB_COUNT" >> "$SUMMARY_OUTPUT_FILE"
