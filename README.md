# Secure Multiparty Computing (MPC) Module for RSA Keypair Generation and Message Decryption 

Secure Multiparty Computing (MPC) is a heated research field in cryptography with the goal of creating methods for multiple parties to jointly contribute to the computation while keeping the input private to each party. RSA encryption algorithm, which requires lots of computations involving multiplication and modular on large prime numbers, is suitable to be modified to working in an MPC scenario. This project aims to implement a containerized MPC module for RSA keypair generation and message decryption using Java.  

Project collaborators are Gyeonjun Lee ([7942jun](https://github.com/7942jun)) Hexiang Geng ([CuriousLocky](https://github.com/CuriousLocky)) and Minghang Lee ([Matchy](https://github.com/matchy233)).

## Commit Message Conventions

There are two popular ways of writing a commit message: [Tim Pope style](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html) (This is also recommended in the official guideline of Git, [Pro Git](https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project)), and the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) style. The latter one is preferred in many large open-source projects since it dovetails [SemVer](https://semver.org/). Here we adopt the Tim Pope style, for it's succinctness.

Here are the 7 rules for writing a good Tim Pope style commit message:

1. Limit the subject line to 50 characters.
2. Capitalize *only* the first letter in the subject line.
3. *Don't* put a period at the end of the subject line.
4. Put a blank line between the subject line and the body.
5. Wrap the body at 72 characters.
6. Use the imperative mood, but not past tense.
7. Describe *what* was done and why, but not *how*.

Read [this nice blog post](https://chris.beams.io/posts/git-commit/) for explanation on why we set up those rules.

## Build and Run the Project

WIP