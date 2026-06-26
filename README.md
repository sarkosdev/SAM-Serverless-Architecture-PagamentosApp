# SAM-Serverless-Architecture-PagamentosApp
Serverless Architecture for Pagamentos Application, using AWS SAM Framework, AWS Lambda, AWS API Gateway, AWS DynamoDB and AWS Amplify


## Professional 'PagamentosApp' Application Architecture - Guide

### 1. Description

'PagamentosApp' is a payment receipts application, that calculates the value of each invoice.
This is the backend serverless architecture solution for this application, using api gateway, four lambdas functions and dynamoDB table.


### 2. Pre-Requirements

1. Install IDE (I installed VS CODE);
2. Install *AWS CLI* locally on your dev machine;
3. Create a IAM User with the requirement permissions in order to perform operations on AWS Console;
4. Configure *AWS CLI* in order to have access to cloud (using IAM Role) locally on your machine;
5. Install YAML Languange support on VS Code;
6. Install and configure AWS SAM
7. Install docker desktop (emolutes cloud testing locally)

### 3. Implementation - Locally

1. Create a github repository;

2. Clone github repository in to your development machine;

3. Create connection between GitHub and AWS Console, CodePipeline and CodeBuild
    - Sign in to AWS Console
    - Make sure Region is selected as the correct one (example: eu-west-1)
    - Move to 'CodePipeline' and press 'Create new Pipeline'
        - Choose 'Build Custom Pipeline'
        - Name: api-pagamentos-pipeline
        - Execution Mode: Queued
        - Source Stage:
            - Source Provider: Github (via Github App)
            - Connection: Connect to Github, define connection name and access your github account and repository
            - Output artifact format: CodePipeline default
            - Next
        - Build stage:
            - Other Build Porviders: AWS CodeBuild
                - Project name: api-pagamento-build-stage
                - Project type: Default project
                - Source: AWS CodePipeline
                - Environment:
                    - Image: Managed image
                    - Compute: Lambda
                    - Operation System: Amazon Linux
                    - Runtime: Java
                    - Image: aws/codebuild/amazonlinux-x86_64-lambda-standard:corretto25
                    - Image Version: last version
                - BuildSpec:
                    - Use a buildspec file: buildspec.yml
                - Service permissions:
                    - Select the Role created on IAM that have the permissions for the build

4. Create project locally using sam template command;
    - $ `sam init --name serverless-api-pagamentos --runtime java25 --dependency-manager maven --app-template hello-world --package-type Zip`

5. Create DynamoDB container locally in order to test application locally;
    - `docker network create sam-local`
    - `docker run -d --name dynamodb-local --network sam-local -p 8000:8000 amazon/dynamodb-local`

6. Lets create DynamoDB table locally; 
    - `$env:AWS_ACCESS_KEY_ID="dummy"`
    - `$env:AWS_SECRET_ACCESS_KEY="dummy"`
    - `$env:AWS_REGION="eu-west-1"`
    - `aws dynamodb create-table --table-name PagamentosTable --attribute-definitions AttributeName=id,AttributeType=S --key-schema AttributeName=id,KeyType=HASH --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000 --region eu-west-1`

7. Check if table was created sucessfully, if you see the 'PagementosTable' on the list, then everything went smooth
    - `aws dynamodb list-tables --endpoint-url http://localhost:8000 --region eu-west-1`

8. Create LambdaFunctions According to our 'Pagamentos' API scenario. Java Code structer used for our architecture. We will use four Lambda Functions instead, to split reponsabilities between them
    - ReadPagamentoFunction
    - CreatePagamentoFunction
    - PagarPagamentoFunction
    - DeletePagamentoFunction


8. Make the changes needed when it comes to your java class and your .yaml file. After changing the java code, allways compile the code again, run the following command inside the Lambda Function root folder 
    - `mvn clean package -DskipTests -e`

9. Test your application locally. The second command launches a local api ready to be tested. Everytime you change template.yaml file you need to rebuild it
    - `sam build`
    - `sam local start-api --template .aws-sam/build/template.yaml --env-vars env.json --docker-network sam-local`

10. Verify locally the values inside our DynamoDB table 
    - `aws dynamodb scan --table-name PagamentosTable --endpoint-url http://localhost:8000  --region eu-west-1`

11. Verify how many items there is on table
    - `aws dynamodb scan --table-name PagamentosTable --select COUNT --endpoint-url http://localhost:8000 --region eu-west-1`

12. Cleaner output where we only retrieve the 'Items' straight from the table
    - `aws dynamodb scan --table-name PagamentosTable --endpoint-url http://localhost:8000 --region eu-west-1 --query "Items"`

13. If you using a powershell cli use this commands instead, in order to add an entry to our DynamoDB table. First command we create the body, and the second we send the body using a POST method throw our API url
    - `$body = @{ processNum = "PROC-CLOUD-101"; processValue = "25.50" } | ConvertTo-Json`
    - `Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:3000/pagamentos" -ContentType "application/json" -Body $body`

15. Check the list of 'Pagamentos' calling GET '/pagamentos' that lists every item in the table
    - `curl.exe -i http://127.0.0.1:3000/pagamentos`

16. Instead we can use the Powershell aproach where we use the similar CLI command that we used to create the entry in the table
    - `Invoke-RestMethod -Method GET -Uri "http://127.0.0.1:3000/pagamentos"`

16. Access the list according to status, either PENDING or PAGO, returning all values from our DynamoDB table (using PowerShell). Use one of either endpoint for both scenarios
    - `Invoke-RestMethod -Method GET -Uri "http://127.0.0.1:3000/pagamentos/status/PENDING"`
    - `Invoke-RestMethod -Method GET -Uri "http://127.0.0.1:3000/pagamentos/status/PAGO"`

17. Process functionality in order to process the payment according to the list of process numbers passed
    - `$body = @{listaProcess = @("PROC-PIPELINE-001", "test-1")} | ConvertTo-Json`
    - `Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:3000/pagamentos/pagar" -ContentType "application/json" -Body $body`

18. If you ever need to delete one record from our DynamoDB 'Pagamentos' table. Chande 'PROC-001' for your process number
    - `Invoke-RestMethod -Method DELETE -Uri "http://127.0.0.1:3000/pagamentos/process/PROC-001"`

19. After the application is fully deployed on AWS Cloud, navigate to CodeBuild, Build projects and select the build that just runned and checked the URL that was generated in order to perform the communication with the api. Next it follows a pratical example of a request on how to create a 'Pagamento' inside our DynamoDB table using the api we just deployed
    - `$api = https://0b6hq996lk.execute-api.eu-west-1.amazonaws.com/pagamentos`
    - `$body = @{ processNum = "PROC-CLOUD-101"; processValue = "25.50" } | ConvertTo-Json`
    - `Invoke-RestMethod -Method POST -Uri $api -ContentType "application/json" -Body $body`

20. Check the entry we just added to the DynamoDB table one of the two requests
    - `Invoke-RestMethod -Method GET -Uri $api`
    - `Invoke-RestMethod -Method GET -Uri $api/status/PENDING`




## TIPS

1. Remove local docker container that emulates our cloud run
    - `docker rm -f dynamodb-local`
