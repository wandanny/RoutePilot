import Foundation
import SwiftData

enum StopStatus: String, Codable, CaseIterable {
  case pending, done, failed
}

enum FailureReason: String, Codable, CaseIterable {
  case notHome = "Not home"
  case refused = "Refused delivery"
  case closed = "Business closed"
  case wrongAddress = "Wrong address"
  case other = "Other"
}

@Model
final class RouteRecord {
  var name: String
  var date: String
  var active: Bool
  @Relationship(deleteRule: .cascade, inverse: \StopRecord.route)
  var stops: [StopRecord] = []

  init(name: String, date: String, active: Bool) {
    self.name = name
    self.date = date
    self.active = active
  }
}

@Model
final class StopRecord {
  var route: RouteRecord?
  var address: String
  var label: String
  var recipient: String
  var phone: String
  var notes: String
  var priority: Int
  var windowStart: String
  var windowEnd: String
  var latitude: Double?
  var longitude: Double?
  var orderIndex: Int
  var pinned: Bool
  var status: StopStatus
  var failureReason: FailureReason?
  var failureNote: String
  var retryAt: String
  var trackingId: String
  var estimatedCoordinates: Bool

  init(
    route: RouteRecord? = nil,
    address: String,
    label: String = "",
    recipient: String = "",
    phone: String = "",
    notes: String = "",
    priority: Int = 0,
    windowStart: String = "",
    windowEnd: String = "",
    latitude: Double? = nil,
    longitude: Double? = nil,
    orderIndex: Int = 0,
    pinned: Bool = false,
    status: StopStatus = .pending,
    failureReason: FailureReason? = nil,
    failureNote: String = "",
    retryAt: String = "",
    trackingId: String = "",
    estimatedCoordinates: Bool = false
  ) {
    self.route = route
    self.address = address
    self.label = label
    self.recipient = recipient
    self.phone = phone
    self.notes = notes
    self.priority = priority
    self.windowStart = windowStart
    self.windowEnd = windowEnd
    self.latitude = latitude
    self.longitude = longitude
    self.orderIndex = orderIndex
    self.pinned = pinned
    self.status = status
    self.failureReason = failureReason
    self.failureNote = failureNote
    self.retryAt = retryAt
    self.trackingId = trackingId
    self.estimatedCoordinates = estimatedCoordinates
  }
}

struct BackupPayload: Codable {
  var routes: [RouteDTO]
  var stops: [StopDTO]
}

struct RouteDTO: Codable {
  var name: String
  var date: String
  var active: Bool
}

struct StopDTO: Codable {
  var address: String
  var label: String
  var recipient: String
  var phone: String
  var notes: String
  var priority: Int
  var windowStart: String
  var windowEnd: String
  var latitude: Double?
  var longitude: Double?
  var orderIndex: Int
  var pinned: Bool
  var status: StopStatus
  var failureReason: FailureReason?
  var failureNote: String
  var retryAt: String
  var trackingId: String
  var estimatedCoordinates: Bool
}
