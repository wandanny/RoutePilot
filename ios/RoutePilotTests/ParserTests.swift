import XCTest
@testable import RoutePilot

final class ParserTests: XCTestCase {
  func testLabelParserExtractsRecipientAddressAndPostcode() {
    let text = """
    Sender: Warehouse
    Recipient Details
    Name: Tan Wei Ming
    Address: No. 12, Jalan Kota
    Taiping
    Postcode: 34000
    Phone: 012-345 6789
    """
    let result = LabelParser.parse(text)
    XCTAssertEqual(result.name, "Tan Wei Ming")
    XCTAssertTrue(result.address.contains("Jalan Kota"))
    XCTAssertTrue(result.address.contains("Taiping"))
    XCTAssertEqual(result.postcode, "34000")
    XCTAssertEqual(result.phone, "012-345 6789")
    XCTAssertTrue(result.warnings.isEmpty)
  }

  func testLabelParserFlagsInferredPostcode() {
    let result = LabelParser.parse("Address: Jalan Kamunting 34000")
    XCTAssertEqual(result.postcode, "34000")
    XCTAssertTrue(result.warnings.contains("Postcode inferred"))
  }

  func testCsvParserHandlesCommaAndSemicolon() {
    let comma = "address,label,recipient\nJalan Kota,Parcel 1,Tan\n"
    let rows = CsvParser.parse(comma)
    XCTAssertEqual(rows.count, 1)
    XCTAssertEqual(rows[0]["address"], "Jalan Kota")
    XCTAssertEqual(rows[0]["label"], "Parcel 1")

    let semicolon = "address;label\nJalan Kota;Parcel 2\n"
    let semiRows = CsvParser.parse(semicolon)
    XCTAssertEqual(semiRows.count, 1)
    XCTAssertEqual(semiRows[0]["label"], "Parcel 2")
  }

  func testAreaValidationRejectsDistantResultsOnlyWhenEnabled() {
    let taiping = (4.85, 100.74)
    XCTAssertTrue(AddressSupport.isPlausible(latitude: 4.86, longitude: 100.75, near: taiping))
    XCTAssertFalse(AddressSupport.isPlausible(latitude: 3.14, longitude: 101.69, near: taiping))
    XCTAssertTrue(AddressSupport.isPlausible(latitude: 3.14, longitude: 101.69, near: nil))
  }

  func testLockPolicyPreventsReordering() {
    let matrix: [[Double]] = [
      [0, 10, 40, 30],
      [10, 0, 20, 35],
      [40, 20, 0, 15],
      [30, 35, 15, 0],
    ]
    XCTAssertNil(RouteOrderingPolicy.reorderedIndices(matrix: matrix, stopCount: 3, locked: true))
    let unlocked = RouteOrderingPolicy.reorderedIndices(matrix: matrix, stopCount: 3, locked: false)
    XCTAssertEqual(unlocked?.count, 3)
    XCTAssertEqual(Set(unlocked ?? []), Set([1, 2, 3]))
  }

  func testRouteSolverEndsAtFarthestEfficientStop() {
    let matrix: [[Double]] = [
      [0, 10, 40, 30],
      [10, 0, 20, 35],
      [40, 20, 0, 15],
      [30, 35, 15, 0],
    ]
    let order = RouteSolver.order(matrix: matrix, stopCount: 3)
    XCTAssertEqual(order.count, 3)
    XCTAssertEqual(Set(order), Set([1, 2, 3]))
    let cost = zip([0] + order, order).reduce(0.0) { $0 + matrix[$1.0][$1.1] }
    XCTAssertLessThan(cost, 80)
  }
}
