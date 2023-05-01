# Kesp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.darkxanter.exposed/kesp-annotations)](https://search.maven.org/artifact/io.github.darkxanter.exposed/kesp-annotations)

**Kesp** is Kotlin Symbol Processor for [Exposed SQL DSL](https://github.com/JetBrains/Exposed/wiki/DSL).
It generates for you DTOs, table mappings and a CRUD repository for an Exposed table.


<!-- TOC -->
* [Features](#features)
* [Annotations](#annotations)
* [How to use](#how-to-use)
  * [Basic example](#basic-example)
  * [Projection example](#projection-example)
  * [DAO example](#dao-example)
* [Setup](#setup)
* [License](#license)
<!-- TOC -->

## Features

- generates table mappings and functions
- generates data classes and interfaces
- generates a CRUD repository
- generates mappings for table projections
- copies KDoc from columns to data class fields
- you can use any custom columns, unlike libraries where you define a data class
  and only a table with supported build-in columns is generated
- generates DAOs

## Annotations

- `@ExposedTable` specifies that code generation will be run for the table
- `@Projection` specifies for which table projection functions should be generated
- `@Id` specifies the primary key of a table. Can be applied to multiple columns.
- `@GeneratedValue` specifies that the column value is generated by a database.
- `@ForeignKey` specifies that the column is foreign key.

## How to use

### Basic example

Our table might look like the following:
```kotlin
/** User account */
object UserTable : LongIdTable("users") {
    /**
     * Username
     */
    val username = varchar("username", 255)

    /** User password */
    val password = varchar("password", 255)

    /** Day of birth */
    val birthDate = date("birth_date").nullable()

    /** Account creation time */
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}
```

To create mapping functions, DTOs and CRUD repository you simply need to add the `@ExposedTable` annotation above the target table.
We also need to add `@GeneratedValue` annotation above the `createdAt` column because it's generated on a database side.

```kotlin
/** User account */
@ExposedTable
@Projection(UserDto::class, updateFunction = true)
object UserTable : LongIdTable("users") {
    /**
     * Username
     */
    val username = varchar("username", 255)

    /** User password */
    val password = varchar("password", 255)

    /** Day of birth */
    val birthDate = date("birth_date").nullable()

    /** Account creation time */
    @GeneratedValue
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}
```

When we build the project we'll have:

- Interfaces and data classes:
  - `UserTableCreate` and `UserTableCreateDto` represent the row model for creating an entry in the table
  - `UserTableFull` and `UserTableFullDto` represent the full table model used to read from the table

```kotlin
/**
 * User account
 */
public interface UserTableCreate {
    /**
     * Username
     */
    public val username: String

    /**
     * User password
     */
    public val password: String

    /**
     * Day of birth
     */
    public val birthDate: LocalDate?
}

/**
 * User account
 */
public data class UserTableCreateDto(
    /**
     * Username
     */
    public override val username: String,
    /**
     * User password
     */
    public override val password: String,
    /**
     * Day of birth
     */
    public override val birthDate: LocalDate? = null,
) : UserTableCreate

/**
 * User account
 */
public interface UserTableFull : UserTableCreate {
    public val id: Long

    /**
     * Account creation time
     */
    public val createdAt: Instant
}

/**
 * User account
 */
public data class UserTableFullDto(
    public override val id: Long,
    /**
     * Username
     */
    public override val username: String,
    /**
     * User password
     */
    public override val password: String,
    /**
     * Day of birth
     */
    public override val birthDate: LocalDate? = null,
    /**
     * Account creation time
     */
    public override val createdAt: Instant,
) : UserTableFull
```

- "to" and "from" mapping extension functions for `ResultRow` and `UpdateBuilder`
```kotlin
public fun ResultRow.toUserTableFullDto(): UserTableFullDto = UserTableFullDto(
  id = this[UserTable.id].value,
  username = this[UserTable.username],
  password = this[UserTable.password],
  birthDate = this[UserTable.birthDate],
  createdAt = this[UserTable.createdAt],
)

public fun ResultRow.toUserTableFullDto(alias: Alias<UserTable>): UserTableFullDto =
    UserTableFullDto(
  id = this[alias[UserTable.id]].value,
  username = this[alias[UserTable.username]],
  password = this[alias[UserTable.password]],
  birthDate = this[alias[UserTable.birthDate]],
  createdAt = this[alias[UserTable.createdAt]],
)

public fun Iterable<ResultRow>.toUserTableFullDtoList(): List<UserTableFullDto> = map {
  it.toUserTableFullDto()
}

public fun Iterable<ResultRow>.toUserTableFullDtoList(alias: Alias<UserTable>):
    List<UserTableFullDto> = map {
  it.toUserTableFullDto(alias)
}

public fun UpdateBuilder<*>.fromDto(dto: UserTableCreate): Unit {
  this[UserTable.username] = dto.username
  this[UserTable.password] = dto.password
  this[UserTable.birthDate] = dto.birthDate
}

public fun UpdateBuilder<*>.fromDto(
  username: String,
  password: String,
  birthDate: LocalDate? = null,
): Unit {
  this[UserTable.username] = username
  this[UserTable.password] = password
  this[UserTable.birthDate] = birthDate
}
```

- `insertDto` and `updateDto` extension functions for the table
```kotlin
public fun UserTable.insertDto(dto: UserTableCreate): Long = UserTable.insertAndGetId {
  it.fromDto(dto)
}.value

public fun UserTable.updateDto(id: Long, dto: UserTableCreate): Int =
    UserTable.update({UserTable.id.eq(id)}) {
  it.fromDto(dto)
}

public fun UserTable.insertDto(
  username: String,
  password: String,
  birthDate: LocalDate? = null,
): Long = UserTable.insertAndGetId {
  it.fromDto(
    username = username,
    password = password,
    birthDate = birthDate,
  )
}.value

public fun UserTable.updateDto(
  id: Long,
  username: String,
  password: String,
  birthDate: LocalDate? = null,
): Int = UserTable.update({UserTable.id.eq(id)}) {
  it.fromDto(
    username = username,
    password = password,
    birthDate = birthDate,
  )
}
```

- CRUD repository
```kotlin
public open class UserTableRepository {
  public fun find(configure: Query.() -> Unit = {},
      `where`: (SqlExpressionBuilder.() -> Op<Boolean>)? = null): List<UserTableFullDto> {

    return transaction {
      if (where != null) {
        UserTable.select(where).apply(configure).toUserTableFullDtoList()
      } else {
        UserTable.selectAll().apply(configure).toUserTableFullDtoList()
      }
    }
  }

  public fun findOne(`where`: SqlExpressionBuilder.() -> Op<Boolean>): UserTableFullDto? {

    return find(where = where).singleOrNull()
  }

  public fun findById(id: Long): UserTableFullDto? {

    return findOne {
      UserTable.id.eq(id)
    }
  }

  public fun create(dto: UserTableCreate): Long = transaction {
    UserTable.insertDto(dto)
  }

  public fun update(id: Long, dto: UserTableCreate): Int = transaction {
    UserTable.updateDto(id, dto)
  }

  public fun deleteById(id: Long): Int = delete {
    UserTable.id.eq(id)
  }

  public fun delete(`where`: UserTable.(ISqlExpressionBuilder) -> Op<Boolean>): Int {

    return transaction {
      UserTable.deleteWhere {
        where(it)
      }
    }
  }
}
```

### Projection example

To create mapping functions and CRUD repository for a table projection,
you need to add the `@Projection` annotation above the target table and point to a projection `KClass`.

```kotlin
/** User account */
@ExposedTable
@Projection(UserDto::class, updateFunction = true)
object UserTable : LongIdTable("users") {
    /**
     * Username
     */
    val username = varchar("username", 255)

    /** User password */
    val password = varchar("password", 255)

    /** Day of birth */
    val birthDate = date("birth_date").nullable()

    /** Account creation time */
    @GeneratedValue
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

data class UserDto(
    val id: Long,
    val username: String,
)
```

After build the project we'll have additional functions:

- "to" mapping extension functions for `ResultRow`
```kotlin
public fun ResultRow.toUserDto(): UserDto = UserDto(
  id = this[UserTable.id].value,
  username = this[UserTable.username],
)

public fun ResultRow.toUserDto(alias: Alias<UserTable>): UserDto = UserDto(
  id = this[alias[UserTable.id]].value,
  username = this[alias[UserTable.username]],
)
```
- if the `updateFunction = true` is set in the `Projection` annotation, it will be generated "from" mapping extension function
```kotlin
public fun UpdateBuilder<*>.fromDto(dto: UserDto): Unit {
  this[UserTable.username] = dto.username
}
```
- and the CRUD repository will look like this
```kotlin
public open class UserTableRepository {
  public fun find(configure: Query.() -> Unit = {},
      `where`: (SqlExpressionBuilder.() -> Op<Boolean>)? = null): List<UserTableFullDto> {

    return transaction {
      if (where != null) {
        UserTable.select(where).apply(configure).toUserTableFullDtoList()
      } else {
        UserTable.selectAll().apply(configure).toUserTableFullDtoList()
      }
    }
  }

  public fun findUserDto(configure: Query.() -> Unit = {},
      `where`: (SqlExpressionBuilder.() -> Op<Boolean>)? = null): List<UserDto> {

    return transaction {
      if (where != null) {
        UserTable.slice(UserTable.id,UserTable.username).select(where).apply(configure).toUserDtoList()
      } else {
        UserTable.slice(UserTable.id,UserTable.username).selectAll().apply(configure).toUserDtoList()
      }
    }
  }

  public fun findOne(`where`: SqlExpressionBuilder.() -> Op<Boolean>): UserTableFullDto? {

    return find(where = where).singleOrNull()
  }

  public fun findById(id: Long): UserTableFullDto? {

    return findOne {
      UserTable.id.eq(id)
    }
  }

  public fun create(dto: UserTableCreate): Long = transaction {
    UserTable.insertDto(dto)
  }

  public fun update(id: Long, dto: UserTableCreate): Int = transaction {
    UserTable.updateDto(id, dto)
  }

  public fun updateUserDto(id: Long, dto: UserDto): Int = transaction {
    UserTable.updateDto(id, dto)
  }

  public fun deleteById(id: Long): Int = delete {
    UserTable.id.eq(id)
  }

  public fun delete(`where`: UserTable.(ISqlExpressionBuilder) -> Op<Boolean>): Int {

    return transaction {
      UserTable.deleteWhere {
        where(it)
      }
    }
  }
}
```

You can find a complete project example in the `example` subdirectory.

### DAO example

To generate a DAO for a table, you need to set the `generateDao = true` in the `@ExposedTable` annotation.

**Example:**

```kotlin
@ExposedTable(generateDao = true)
object ArticleTable : IntIdTable() {
    val title = text("title")

    @ForeignKey(UserTable::class)
    val userId = reference("user_id", UserTable)

    @GeneratedValue
    val createdAt = timestamp("created_at")
}

@ExposedTable(generateDao = true)
object CommentTable : IntIdTable() {
    /** Annotation [ForeignKey] is not required if column type is simple `Column<Int>` */
    val articleId = integer("article_id").references(ArticleTable.id)
    val message = text("message")

    /** Annotation [ForeignKey] is required if column type is `Column<Entity<*>>` */
    @ForeignKey(UserTable::class)
    val userId = reference("user_id", UserTable)
}

@ExposedTable(generateDao = true)
object UserTable : IntIdTable("users") {
    val username = text("username")
}

```

**Custom DAO:**

```kotlin
class ArticleDao(id: EntityID<Int>) : ArticleTableDaoBase(id) {
    companion object : EntityClass<Int, ArticleDao>(ArticleTable)

    var user by UserTableDao referencedOn ArticleTable.userId
}

```

**Generated code:**

ArticleTableDao.kt:
```kotlin
public abstract class ArticleTableDaoBase(
    id: EntityID<Int>,
) : Entity<Int>(id) {
    public var title: String by ArticleTable.title

    public var userId: EntityID<Int> by ArticleTable.userId

    public val createdAt: Instant by ArticleTable.createdAt
}

public class ArticleTableDao(
    id: EntityID<Int>,
) : ArticleTableDaoBase(id) {
    public companion object : EntityClass<Int, ArticleTableDao>(ArticleTable)
}

public fun ArticleTableDaoBase.fromDto(dto: ArticleTableCreate): Unit {
    this.title = dto.title
    this.userId = DaoEntityID(dto.userId, UserTable)
}

public fun ArticleTableDaoBase.toArticleTableFullDto(): ArticleTableFullDto = ArticleTableFullDto(
    id = id.value,
    title = title,
    userId = userId.value,
    createdAt = createdAt,
)

public fun Iterable<ArticleTableDaoBase>.toArticleTableFullDtoList(): List<ArticleTableFullDto> = map {
    it.toArticleTableFullDto()
}

```

UserTableDao.kt:

```kotlin
public abstract class UserTableDaoBase(
  id: EntityID<Int>,
) : Entity<Int>(id) {
  public var username: String by UserTable.username
}

public class UserTableDao(
  id: EntityID<Int>,
) : UserTableDaoBase(id) {
  public companion object : EntityClass<Int, UserTableDao>(UserTable)
}

public fun UserTableDaoBase.fromDto(dto: UserTableCreate): Unit {
  this.username = dto.username
}

public fun UserTableDaoBase.toUserTableFullDto(): UserTableFullDto = UserTableFullDto(
  id = id.value,
  username = username,
)

public fun Iterable<UserTableDaoBase>.toUserTableFullDtoList(): List<UserTableFullDto> = map {
  it.toUserTableFullDto()
}
```

CommentTableDao.kt:

```kotlin
public abstract class CommentTableDaoBase(
  id: EntityID<Int>,
) : Entity<Int>(id) {
  public var articleId: Int by CommentTable.articleId

  public var message: String by CommentTable.message

  public var userId: EntityID<Int> by CommentTable.userId
}

public class CommentTableDao(
  id: EntityID<Int>,
) : CommentTableDaoBase(id) {
  public companion object : EntityClass<Int, CommentTableDao>(CommentTable)
}

public fun CommentTableDaoBase.fromDto(dto: CommentTableCreate): Unit {
  this.articleId = dto.articleId
  this.message = dto.message
  this.userId = DaoEntityID(dto.userId, UserTable)
}

public fun CommentTableDaoBase.toCommentTableFullDto(): CommentTableFullDto = CommentTableFullDto(
  id = id.value,
  articleId = articleId,
  message = message,
  userId = userId.value,
)

public fun Iterable<CommentTableDaoBase>.toCommentTableFullDtoList(): List<CommentTableFullDto> = map {
  it.toCommentTableFullDto()
}

```

## Setup

Add KSP plugin to your module's `build.gradle.kts`:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "1.7.22-1.0.8"
}
```

Add `Maven Central` to the repositories blocks in your project's `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}
```

Add `kesp` dependencies:

```kotlin
dependencies {
    compileOnly("io.github.darkxanter.exposed:kesp-annotations:0.9.0")
    ksp("io.github.darkxanter.exposed:kesp-processor:0.9.0")
}
```

To access generated code from KSP, you need to set up the source path into your module's `build.gradle.kts` file:

```kotlin
sourceSets.configureEach {
    kotlin.srcDir("$buildDir/generated/ksp/$name/kotlin/")
}
```

To create DTO with the `kotlinx.serialization.Serializable` annotation, add to `build.gradle.kts`:

```kotlin
ksp {
    arg("kesp.kotlinxSerialization", "true")
}
```

---

License
======
    Copyright 2022 Sergey Shumov

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

           http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
