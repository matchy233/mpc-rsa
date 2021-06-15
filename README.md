# Secure Multiparty Computing (MPC) Module for RSA Keypair Generation and Message Decryption

![GitHub top language](https://img.shields.io/github/languages/top/7942jun/mpc-project?color=orange) ![last commit](https://img.shields.io/github/last-commit/7942jun/mpc-project) ![GitHub Workflow Status](https://img.shields.io/github/workflow/status/7942jun/mpc-project/Java%20CI) ![Codecov](https://img.shields.io/codecov/c/github/7942jun/mpc-project) ![Docker Pulls](https://img.shields.io/docker/pulls/matchy233/mpc-project_manager?label=docker%20pulls&logo=manager) ![Docker Pulls](https://img.shields.io/docker/pulls/matchy233/mpc-project_worker?label=docker%20pulls&logo=manager)

Secure Multiparty Computing (MPC) is a heated research field in cryptography with the goal of creating methods for
multiple parties to jointly contribute to the computation while keeping the input private to each party.

RSA encryption algorithm, which requires lots of computations involving multiplication and modulus on large prime
numbers, is suitable to be modified to working in an MPC scenario.

This project aims to implement a containerized MPC module for RSA keypair generation and message decryption.

Project collaborators are Gyeonjun Lee ([7942jun](https://github.com/7942jun)), Hexiang
Geng ([CuriousLocky](https://github.com/CuriousLocky)) and Minghang Lee ([Matchy](https://github.com/matchy233)).

## Build and Run the Project

### Run from Docker image

The Docker image is available in the Docker hub. You can simply do a `docker pull` to acquire the images.

```bash
$ docker pull matchy233/mpc-project_manager
$ docker pull matchy233/mpc-project_worker
```

It's also possible to build the image from source. We use Docker compose to distribute `manager` and `worker` conveniently. [Install Docker compose](https://docs.docker.com/compose/install/) if not already installed and simply run:

```bash
$ docker-compose build
```

Afterwards, you should be able to check the generated images by

```bash
$ docker images
REPOSITORY            TAG           IMAGE ID       CREATED             SIZE
mpc-project_manager   latest        56c59ec8a29d   1 minute ago        445MB
mpc-project_worker    latest        3432307d295f   2 minutes ago       244MB
```

To run the application, a simple `docker run` will easily get the manager or worker set up. 

The following 2 commands set up 2 containers. One is named `manager` running in manager mode with the exposed port published to port `8350` of the local machine; the other is named `worker1` running in worker mode with the exposed port published to port `8353` of the local machine. 
These containers are then accessible through `<local_machine_IP>:<published_port>` from other machines.

```bash
$ docker run --rm --it --name manager mpc-project_manager -p 8350:8080
$ docker run --rm --itd --name worker1 mpc-project_worker -p 8353:8080
```

### Build from source

We use `Gradle` to manage and build the project. Thus, you can simply build the project from source with `Gradle`. Simply run the following command if you have `Gradle` installed on your
machine:

```bash
$ gradle clean install
```

If you don't have `gradle` installed, you can build with the `Gradle` wrapper:

```bash
$ gradlew clean install
```

After running either command, you can find the executable in `/app/build/install/`. 

Note that the way of compiling and running the application is subject to change.

## Commit Message Conventions

There are two popular ways of writing a commit
message: [Tim Pope style](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html) (This is also
recommended in the official guideline of
Git, [Pro Git](https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project)), and
the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) style. The latter one is preferred in many
large open-source projects since it dovetails [SemVer](https://semver.org/). Here we adopt the Tim Pope style, for its
succinctness.

Here are the 7 rules for writing a good Tim Pope style commit message:

1. Limit the subject line to 50 characters.
2. Capitalize *only* the first letter in the subject line.
3. *Don't* put a period at the end of the subject line.
4. Put a blank line between the subject line and the body.
5. Wrap the body at 72 characters.
6. Use the imperative mood, but not past tense.
7. Describe *what* was done and why, but not *how*.

Read [this nice blog post](https://chris.beams.io/posts/git-commit/) for explanation on why we set up those rules.

## Reference

1. Malkin, M., Wu, T. D., & Boneh, D. (1999, February). Experimenting with Shared Generation of RSA Keys. In **NDSS** (*The Network and Distributed System Security Symposium*).
2. Boneh, D., & Franklin, M. (1997, August). Efficient generation of shared RSA keys. In **CRYPTO** (*Annual
   international cryptology conference*) (pp. 425-439). Springer, Berlin, Heidelberg.
3. Catalano, D., Gennaro, R., & Halevi, S. (2000, May). Computing inverses over a shared secret modulus. In **Eurocrypt**(*International Conference on the Theory and Applications of Cryptographic Techniques*) (pp. 190-206).
   Springer, Berlin, Heidelberg.
