package xyz.sky731.programming.lab8

import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.PriorityBlockingQueue
import java.util.logging.LogRecord
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.javaType

@Target(AnnotationTarget.CLASS)
annotation class Table(val name: String)

@Target(AnnotationTarget.PROPERTY)
annotation class Id

@Target(AnnotationTarget.PROPERTY)
annotation class OneToMany(val cls: KClass<*>, val foreignKey: String)

class SimpleORM(url: String, username: String, password: String) {
  val connection: Connection = DriverManager.getConnection(url, username, password)

  /**
   * @param T is a base type
   * [T] class may contain a few fields which marked with @OneToMany annotation
   **/
  inline fun <reified T : Any> createTable() {
    createTableFromClass(T::class)
  }

  fun createTableFromClass(cls: KClass<*>, foreignKeyRef: Pair<String, String>? = null) {
    val tableName = getTableName(cls)
    val fields = cls.declaredMemberProperties.filter { it.annotations.any { it is OneToMany } }


    val str = cls.declaredMemberProperties.filterNot {
      it.annotations.any { annotation -> annotation is OneToMany }
    }.map {
      var type = convert(it.returnType.javaType.typeName)
      if (it.annotations.any { it is Id }) {
        type = "serial primary key"
      }
      it.name + " " + type + if (it.returnType.isMarkedNullable) "" else " not null"
    }.let { tableFields ->
      if (foreignKeyRef != null)
        tableFields + (foreignKeyRef.first + " integer references " + foreignKeyRef.second
            + " on delete cascade on update cascade")
      else tableFields
    }.joinToString(",")

    connection.createStatement().executeUpdate("create table $tableName($str)")

    fields.forEach { field ->
      val childAnnot = field.annotations.find { it is OneToMany } as OneToMany
      createTableFromClass(childAnnot.cls, Pair(childAnnot.foreignKey, tableName))
    }
  }


  inline fun <reified T : Any> selectAll(): PriorityBlockingQueue<T> {
    return selectAllFromClass(T::class) as PriorityBlockingQueue<T>
  }

  fun selectAllFromClass(cls: KClass<*>): PriorityBlockingQueue<Any> {
    val tableName = getTableName(cls)
    val statement = connection.prepareStatement("select * from $tableName")
    val response = statement.executeQuery()
    val queue = PriorityBlockingQueue<Any>(createObjects(cls, response))

    return queue
  }

  fun createObjects(cls: KClass<*>, result: ResultSet): List<Any> {
    val list = ArrayList<Any>()
    while (result.next()) {
      val constr = cls.constructors.elementAt(0)
      val recordParams = constr.parameters.map {
        when (it.type.javaType.typeName) {
          "java.lang.String" -> result.getString(it.name)
          "java.time.ZonedDateTime" -> {
            val (datetime, offset) = """(.+)([+-]\d\d)$""".toRegex().matchEntire(result.getString(it.name))!!.destructured
            val datetimeLocal = LocalDateTime.parse(datetime.replace(" ", "T"))
            val zoneId = ZoneId.of(offset)
            ZonedDateTime.ofLocal(datetimeLocal, zoneId, ZoneOffset.of(offset))
          }
          "int" -> result.getInt(it.name)
          "boolean" -> result.getBoolean(it.name)
          else -> result.getObject(it.name)
        }
      }.toTypedArray()
      val record = constr.call(*recordParams)
      list.add(record)
      val id = cls.declaredMemberProperties.find { it.annotations.any { it is Id } }?.let { it as KProperty1<Any, Any> }?.get(record)

      val assocs = cls.declaredMemberProperties.filter { it.annotations.any { it is OneToMany } }
      assocs.forEach {
        val annot = it.annotations.find { it is OneToMany } as OneToMany

        val query = "select * from ${getTableName(annot.cls)} where ${annot.foreignKey}=$id"
        val statement = connection.prepareStatement(query)
        if (it is KMutableProperty1)
          (it as KMutableProperty1<Any, Any>).set(record, createObjects(annot.cls, statement.executeQuery()))
        else
          throw RuntimeException("OneToMany must be var")
      }
    }
    return list
  }

  /**
   * Inserts [record] to db
   * [record] may have a few ArrayList as his fields, which should be marked with @OneToMany annotation
   * @param T is a base element's type
   */
  inline fun <reified T : Any> insert(record: T) : T = insertFromClass(record, T::class) as T

  fun insertFromClass(record: Any, cls: KClass<*>, foreignKey: Pair<String, Int>? = null) : Any {
    val tableName = getTableName(cls)
    val idField = cls.declaredMemberProperties.find { it.annotations.any { it is Id } }!!
    val properties = cls.declaredMemberProperties
        .filterNot { it.annotations.any { it is OneToMany } }
        .filterNot { it.annotations.any { it is Id } && (it as KProperty1<Any, Any>).get(record) == null }

    val fields = properties.map { it.name }.let { fields ->
      if (foreignKey != null) fields + foreignKey.first
      else fields
    }
    val values = properties.map { (it as KProperty1<Any, Any>).get(record) }.let { values ->
      if (foreignKey != null) values + foreignKey.second
      else values
    }

    val fieldsOneToMany = cls.declaredMemberProperties
        .filter { it.annotations.any { annotation -> annotation is OneToMany } }

    val statement = connection.prepareStatement("insert into $tableName (" + fields
        .joinToString(", ") + ") values (" + values.map { "?" }.joinToString(", ") + ") returning ${idField.name}")
    values.forEachIndexed { i, value ->
      if (value.javaClass.typeName == "java.time.ZonedDateTime")
        statement.setObject(i + 1, (value as ZonedDateTime).toOffsetDateTime())
      else
        statement.setObject(i + 1, value)
    }

    val result = statement.executeQuery()
    result.next()
    val id = result.getInt(1)
    if (idField is KMutableProperty1)
      (idField as KMutableProperty1<Any, Any>).set(record, id)
    else
      throw RuntimeException("Id must be var")

    fieldsOneToMany.forEach {
      val foreignName = (it.annotations.find { it is OneToMany } as OneToMany).foreignKey

      val foreignKey: Pair<String, Int> = Pair(foreignName, id)
      val childType = (it.annotations.find { it is OneToMany } as OneToMany).cls
      val list = (it as KProperty1<Any, Any>).get(record) as List<Any>?
      list?.forEach {
        insertFromClass(it, childType, foreignKey)
     }
    }

    return record
  }

  /**
   * Finds [T] object in db with specific primary key
   *
   * @return [T] object if found in db, otherwise - null
   * @param id is [T]'s primary key
   */
  inline fun <reified T : Any> findById(id: Int): T? {
    val tableName = getTableName(T::class as KClass<*>)
    val primaryKey = T::class.declaredMembers.find { it.annotations.any { it is Id } }?.name
        ?: throw IllegalArgumentException("@Id annotation is missing")
    val statement = connection.prepareStatement("select * from $tableName where $primaryKey=$id")
    val response = statement.executeQuery()

    return createObjects(T::class, response).firstOrNull() as T?
  }


  fun <T: Any> update(record: T) {
    val tableName = getTableName(record::class)
    val properties = record::class.declaredMemberProperties
        .filterNot { it.annotations.any { it is OneToMany } }
        .filterNot { it.annotations.any { it is Id } }

    val fields = properties.map { it.name + " = ?" }
    val values = properties.map { (it as KProperty1<T, *>).get(record) }

    val idField = record::class.declaredMemberProperties.find { it.annotations.any { it is Id } }!! as KProperty1<Any, Any>

    val statement = connection.prepareStatement("update $tableName set " + fields.joinToString(", ")
        + " where ${idField.name}=${idField.get(record)}")

    values.forEachIndexed { i, value ->
      if (value?.javaClass?.typeName == "java.time.ZonedDateTime")
        statement.setObject(i + 1, (value as ZonedDateTime).toOffsetDateTime())
      else
        statement.setObject(i + 1, value)
    }

    statement.executeUpdate()
  }

  inline fun <reified T: Any> dropTable() { dropTableWithClass(T::class) }

  fun dropTableWithClass(cls: KClass<*>) {
    val tableName = getTableName(cls)
    val assocs = cls.declaredMemberProperties.filter { it.annotations.any { it is OneToMany } }
    assocs.forEach { dropTableWithClass(it.javaClass.kotlin) }
    val statement = connection.prepareStatement("drop table if exists $tableName")
    statement.executeUpdate()
  }

  /**
   * Deletes [T] object from db by primary key
   *
   * @param id is primary key with which we will delete [T] object from db
   * @param T is parent element's type
   * @param U is child element's type
   */
  inline fun <reified T: Any> deleteById(id: Int) {
    val tableName = getTableName(T::class as KClass<*>)
    val field = T::class.declaredMembers.find { it.annotations.any {annotation -> annotation is Id }  }?.name
        ?: throw IllegalArgumentException("@Id annotation is missing")
    val statement = connection.prepareStatement("delete from $tableName where $field=$id")
    statement.executeUpdate()
  }

  private fun convert(string: String) = when (string) {
    "java.sql.Timestamp" -> "timestamp"
    "java.lang.String" -> "text"
    "int" -> "integer"
    "java.time.ZonedDateTime" -> "timestamptz"
    else -> string
  }

  /** Gets name value from "Table" annotation */
  fun getTableName(cls: KClass<*>) = cls.annotations.find { it is Table }?.let { (it as Table).name.toLowerCase() }
      ?: throw IllegalArgumentException("This class is not mapped as Table")
}
