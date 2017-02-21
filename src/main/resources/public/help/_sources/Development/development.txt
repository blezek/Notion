.. include:: ../global.rst

Development
===========

To create a release:

#. Bump the release number in `edu.mayo.qia.pacs.Notion` using `Semantic Versioning <http://semver.org/>`_
#. create a `release/#.#.#` branch, where `#.#.#` is the release number
#. merge `release/#.#.#` into `master`
#. tag (`git tag #.#.#`) the `master` branch
#. build distribution (`make dist`)
