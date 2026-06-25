# SAM-Serverless-Architecture-PagamentosApp
Serverless Architecture for Pagamentos Application, using AWS SAM Framework, AWS Lambda, AWS API Gateway, AWS DynamoDB and AWS Amplify


## Professional 'PagamentosApp' Application Architecture - Guide

### 1. Description

'PagamentosApp' is a payment receipts application, that calculates the value of each invoice.

### 2. Pre-Requirements

1. Install IDE (I installed VS CODE);
2. Install *AWS CLI* locally on your dev machine;
3. Create a IAM User with the requirement permissions in order to perform operations on AWS Console;
4. Configure *AWS CLI* in order to have access to cloud (using IAM Role) locally on your machine;
5. Install YAML Languange support on VS Code;
6. Install and configure AWS SAM
7. Install docker desktop (emolutes cloud testing locally)

### 3. Implementation

1. Create a github repository;

2. Clone github repository in to your development machine;

3. Create connection between GitHub and AWS Console
    - Sign in to AWS Console
    - Make sure Region is selected as the correct one (example: eu-west-1)
    - Move to 'CodePipeline' and press 'Create new Pipeline'
        - Source Provider: Github (via Github App)
        - Connection: Connect to Github, define connection name and access your github account and repository
        - Output artifact format: CodePipeline default
        - Next
        - Leave the rest as default and Create Pipeline
    - 


3. Create project using sam template command;
    - $ `sam init --name serverless-api-pagamentos --runtime java25 --dependency-manager maven --app-template hello-world --package-type Zip`

4. Create DynamoDB container locally in order to test application locally;
    - `docker network create sam-local`
    - `docker run -d --name dynamodb-local --network sam-local -p 8000:8000 amazon/dynamodb-local`

5. Lets create DynamoDB table locally; 
    - `$env:AWS_ACCESS_KEY_ID="dummy"`
    - `$env:AWS_SECRET_ACCESS_KEY="dummy"`
    - `$env:AWS_REGION="eu-west-1"`
    - `aws dynamodb create-table --table-name PagamentosTable --attribute-definitions AttributeName=id,AttributeType=S --key-schema AttributeName=id,KeyType=HASH --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000 --region eu-west-1`

6. Check if table was created sucessfully, if you see the 'PagementosTable' on the list, then everything went smooth
    - `aws dynamodb list-tables --endpoint-url http://localhost:8000 --region eu-west-1`

7. Make the changes needed when it comes to your java class and your .yaml file. After changing the java code, allways compile the code again
    - `mvn clean package -DskipTests -e`

