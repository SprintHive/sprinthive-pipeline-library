#!/usr/bin/groovy

def call(config) {

  ciNode {
    stage("Terraform Plan") {
      terraformPlan([
      ])
    }
  }
}
